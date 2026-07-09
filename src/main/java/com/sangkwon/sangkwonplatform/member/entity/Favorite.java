package com.sangkwon.sangkwonplatform.member.entity;

import com.sangkwon.sangkwonplatform.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "FAVORITE",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_FAVORITE_MEMBER_TRDAR",
                columnNames = {"MEMBER_ID", "TRDAR_CD"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Favorite extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FAVORITE_ID")
    private Long favoriteId;

    @Column(name = "MEMBER_ID", nullable = false)
    private Long memberId;

    @Column(name = "TRDAR_CD", nullable = false, length = 20)
    private String trdarCd;

    private Favorite(Long memberId, String trdarCd) {
        this.memberId = memberId;
        this.trdarCd = trdarCd;
    }

    public static Favorite create(Long memberId, String trdarCd) {
        return new Favorite(memberId, trdarCd);
    }
}
