package com.sangkwon.sangkwonplatform.franchisedisclosure.dto.response;

import com.sangkwon.sangkwonplatform.franchisedisclosure.entity.FranchiseDisclosure;

public record FranchiseDisclosureResponse(
        String disclosureSn,
        String corpNm,
        String brandNm,
        String bizRegNo,
        String viewerUrl
) {
    public static FranchiseDisclosureResponse from(FranchiseDisclosure f) {
        return new FranchiseDisclosureResponse(
                f.getDisclosureSn(),
                f.getCorpNm(),
                f.getBrandNm(),
                f.getBizRegNo(),
                f.getViewerUrl()
        );
    }
}
