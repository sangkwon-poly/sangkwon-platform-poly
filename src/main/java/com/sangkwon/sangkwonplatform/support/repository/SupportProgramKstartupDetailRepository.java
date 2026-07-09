package com.sangkwon.sangkwonplatform.support.repository;

import com.sangkwon.sangkwonplatform.support.entity.SupportProgramKstartupDetail;
import com.sangkwon.sangkwonplatform.support.entity.SupportProgramId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportProgramKstartupDetailRepository
        extends JpaRepository<SupportProgramKstartupDetail, SupportProgramId> {
}
