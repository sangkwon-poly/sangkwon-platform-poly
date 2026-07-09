package com.sangkwon.sangkwonplatform.member.entity;

import java.time.LocalDateTime;

import com.sangkwon.sangkwonplatform.global.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "SEARCH_LOG")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SEARCH_ID")
    private Long searchId;

    @Column(name = "MEMBER_ID")
    private Long memberId;

    @Column(name = "KEYWORD", nullable = false, length = 200)
    private String keyword;

    @Column(name = "TRDAR_CD", length = 20)
    private String trdarCd;

    @Column(name = "SEARCHED_AT", nullable = false)
    private LocalDateTime searchedAt;

    private SearchLog(Long memberId, String keyword, String trdarCd) {
        this.memberId = memberId;
        this.keyword = keyword;
        this.trdarCd = trdarCd;
        this.searchedAt = LocalDateTime.now();
    }

    public static SearchLog create(Long memberId, String keyword, String trdarCd) {
        return new SearchLog(memberId, keyword, trdarCd);
    }
}
