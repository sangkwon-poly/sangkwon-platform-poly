package com.sangkwon.sangkwonplatform.support.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupportProgramTypeTabTest {

    @Test
    void fromRawValue는_목록_필터와_같은_exact_매칭으로_탭을_고른다() {
        // rawValues에 그대로 있는 값만 해당 탭으로, 배지 카운트가 목록 필터(IN)와 어긋나지 않는다
        assertThat(SupportProgramTypeTab.fromRawValue("정책자금")).isEqualTo(SupportProgramTypeTab.FUND);
        assertThat(SupportProgramTypeTab.fromRawValue("기술개발(R&amp;D)")).isEqualTo(SupportProgramTypeTab.RND);
    }

    @Test
    void fromRawValue는_열거되지_않은_변형은_ETC로_보낸다() {
        // of()는 정규화로 폭넓게 잡지만, 필터가 못 잡는 값을 배지에서만 세면 숫자가 어긋난다.
        // '·'(U+00B7) 변형은 rawValues의 'ㆍ'(U+318D)와 exact로 다르므로 ETC로 떨어져 필터와 일치한다.
        assertThat(SupportProgramTypeTab.fromRawValue("융자·보증")).isEqualTo(SupportProgramTypeTab.ETC);
        assertThat(SupportProgramTypeTab.fromRawValue("기술개발사업")).isEqualTo(SupportProgramTypeTab.ETC);
        assertThat(SupportProgramTypeTab.fromRawValue(null)).isEqualTo(SupportProgramTypeTab.ETC);
    }
}
