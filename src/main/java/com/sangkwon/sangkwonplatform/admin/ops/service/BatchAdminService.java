package com.sangkwon.sangkwonplatform.admin.ops.service;

import com.sangkwon.sangkwonplatform.admin.account.dto.session.AdminSession;
import com.sangkwon.sangkwonplatform.admin.ops.AuditAction;
import com.sangkwon.sangkwonplatform.admin.ops.dto.BatchCatalogResponse;
import com.sangkwon.sangkwonplatform.admin.ops.dto.BatchLogResponse;
import com.sangkwon.sangkwonplatform.global.batch.BatchJobLog;
import com.sangkwon.sangkwonplatform.global.batch.BatchJobLogRepository;
import com.sangkwon.sangkwonplatform.global.batch.BatchStatus;
import com.sangkwon.sangkwonplatform.global.batch.Dataset;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// 적재 카탈로그 조회 + 앱 네이티브 적재 트리거. 실제 실행은 비동기(BatchAsyncRunner)로 넘긴다.
@Service
@RequiredArgsConstructor
public class BatchAdminService {

    private final BatchJobLogRepository batchJobLogRepository;
    private final BatchAsyncRunner batchAsyncRunner;
    private final AdminAuditService adminAuditService;
    private final DatasetStatsReader datasetStatsReader;

    // 실행 중인 데이터셋 코드. DB의 RUNNING 기록이 비동기로 늦게 남는 사이의 중복 트리거를 인프로세스에서 원자적으로 막는다.
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    // 데이터셋 카탈로그: 레지스트리 전체 + 실테이블 현황(건수/적재시각/데이터 최신) + 앱 적재 실행 상태
    @Transactional(readOnly = true)
    public List<BatchCatalogResponse> catalog() {
        return Arrays.stream(Dataset.values())
                .map(d -> BatchCatalogResponse.of(d,
                        datasetStatsReader.read(d),
                        batchJobLogRepository.findFirstByDatasetCdOrderByStartedAtDesc(d.code()).orElse(null)))
                .toList();
    }

    // 데이터셋 상세: 최근 실행 이력
    @Transactional(readOnly = true)
    public List<BatchLogResponse> history(String code) {
        Dataset dataset = require(code);
        return batchJobLogRepository.findByDatasetCdOrderByStartedAtDesc(dataset.code(), Limit.of(20))
                .stream().map(BatchLogResponse::from).toList();
    }

    // 앱 적재 트리거: APP 티어만, 같은 데이터셋이 진행 중이면 거부. 감사 로그를 남기고 비동기로 실행한다.
    @Transactional
    public void run(String code, AdminSession admin, HttpServletRequest request) {
        Dataset dataset = require(code);
        if (!dataset.appRunnable()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "앱에서 실행할 수 없는 데이터셋입니다. 오프라인 스크립트로 적재하세요.");
        }
        if (batchJobLogRepository.existsByDatasetCdAndStatus(dataset.code(), BatchStatus.RUNNING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 적재가 진행 중입니다.");
        }
        // DB 검사와 비동기 RUNNING 기록 사이의 경쟁을 막기 위해 실행 슬롯을 원자적으로 선점한다
        if (!inFlight.add(dataset.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 적재가 진행 중입니다.");
        }
        try {
            adminAuditService.record(admin.adminId(), AuditAction.BATCH_RUN,
                    "BATCH_JOB", dataset.code(), dataset.jobName() + " 수동 실행", request);
            batchAsyncRunner.run(dataset, admin.loginId(), () -> inFlight.remove(dataset.code()));
        } catch (RuntimeException e) {
            inFlight.remove(dataset.code()); // 트리거 실패 시 예약 해제(성공 시엔 실행 종료 후 해제)
            throw e;
        }
    }

    // 스테일 RUNNING 초기화: 프로세스는 살아 있는데 배치만 멈춰 재적재가 막힌 경우의 수동 탈출구.
    // 해당 데이터셋의 RUNNING 이력을 FAILED로 내리고 인프로세스 예약도 해제한다.
    @Transactional
    public int reset(String code, AdminSession admin, HttpServletRequest request) {
        Dataset dataset = require(code);
        List<BatchJobLog> running = batchJobLogRepository.findByDatasetCdAndStatus(dataset.code(), BatchStatus.RUNNING);
        running.forEach(log -> log.fail("관리자 수동 초기화"));
        batchJobLogRepository.saveAll(running);
        inFlight.remove(dataset.code());
        adminAuditService.record(admin.adminId(), AuditAction.BATCH_RESET,
                "BATCH_JOB", dataset.code(), "RUNNING " + running.size() + "건 초기화", request);
        return running.size();
    }

    private Dataset require(String code) {
        return Dataset.byCode(code).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "알 수 없는 데이터셋입니다: " + code));
    }
}
