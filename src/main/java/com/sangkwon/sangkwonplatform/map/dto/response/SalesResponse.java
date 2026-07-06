package com.sangkwon.sangkwonplatform.map.dto.response;

import com.sangkwon.sangkwonplatform.map.entity.Sales;

public record SalesResponse(
        String stdrYyquCd,
        String trdarCd,
        String indutyCd,
        Long thsmonSelngAmt,
        Long thsmonSelngCo,
        Long monSelngAmt,
        Long tuesSelngAmt,
        Long wedSelngAmt,
        Long thurSelngAmt,
        Long friSelngAmt,
        Long satSelngAmt,
        Long sunSelngAmt,
        Long tmzon0006SelngAmt,
        Long tmzon0611SelngAmt,
        Long tmzon1114SelngAmt,
        Long tmzon1417SelngAmt,
        Long tmzon1721SelngAmt,
        Long tmzon2124SelngAmt,
        Long mlSelngAmt,
        Long fmlSelngAmt,
        Long agrde10SelngAmt,
        Long agrde20SelngAmt,
        Long agrde30SelngAmt,
        Long agrde40SelngAmt,
        Long agrde50SelngAmt,
        Long agrde60AboveSelngAmt
) {
    public static SalesResponse from(Sales s) {
        return new SalesResponse(
                s.getStdrYyquCd(),
                s.getTrdarCd(),
                s.getIndutyCd(),
                s.getThsmonSelngAmt(),
                s.getThsmonSelngCo(),
                s.getMonSelngAmt(),
                s.getTuesSelngAmt(),
                s.getWedSelngAmt(),
                s.getThurSelngAmt(),
                s.getFriSelngAmt(),
                s.getSatSelngAmt(),
                s.getSunSelngAmt(),
                s.getTmzon0006SelngAmt(),
                s.getTmzon0611SelngAmt(),
                s.getTmzon1114SelngAmt(),
                s.getTmzon1417SelngAmt(),
                s.getTmzon1721SelngAmt(),
                s.getTmzon2124SelngAmt(),
                s.getMlSelngAmt(),
                s.getFmlSelngAmt(),
                s.getAgrde10SelngAmt(),
                s.getAgrde20SelngAmt(),
                s.getAgrde30SelngAmt(),
                s.getAgrde40SelngAmt(),
                s.getAgrde50SelngAmt(),
                s.getAgrde60AboveSelngAmt()
        );
    }
}
