// 지도 화면 공통 유틸

async function apiData(path, signal) {
    const res = await fetch(path, signal ? { signal: signal } : undefined);
    if (!res.ok) {
        throw new Error(path + " 응답 " + res.status);
    }
    return (await res.json()).data;
}

// /api/sales는 { items, returned, limit, truncated } 형태다. 기존 배열 호출부 호환용.
function salesRows(data) {
    if (Array.isArray(data)) {
        return data;
    }
    return data && Array.isArray(data.items) ? data.items : [];
}

// 성장 방향 변화지표 코드. LL 다이나믹, LH 상권확장 (라벨은 서버 changeIxNm 사용)
const CHANGE_UP = new Set(["LL", "LH"]);

function latestQuarter(rows) {
    return rows.reduce((max, r) => (r.stdrYyquCd > max ? r.stdrYyquCd : max), "");
}

// "20261" -> "2026 1분기"
function quarterLabel(code) {
    return code ? code.slice(0, 4) + " " + code.slice(4) + "분기" : "";
}

// "20244" -> "20251". 전분기 대비 계산이 결측 분기를 건너뛰지 않는지 확인할 때 쓴다
function nextQuarter(code) {
    const y = +code.slice(0, 4);
    const q = +code.slice(4);
    return q === 4 ? (y + 1) + "1" : "" + y + (q + 1);
}

function fmtEok(won) {
    if (won == null) {
        return "-";
    }
    const eok = won / 1e8;
    // 작은 업종은 억 단위 반올림이 0이 되므로 소수 한 자리로
    if (eok > 0 && eok < 10) {
        return eok.toFixed(1);
    }
    return Math.round(eok).toLocaleString();
}

function fmtMan(n) {
    return n != null ? Math.round(n / 1e4).toLocaleString() : "-";
}

// 비교함 저장
function cmpList() {
    try {
        return JSON.parse(localStorage.getItem("cmpIds") || "[]");
    } catch (e) {
        return [];
    }
}

function cmpSave(ids) {
    localStorage.setItem("cmpIds", JSON.stringify(ids.slice(0, 4)));
}

function cmpAdd(trdarCd) {
    const ids = cmpList();
    if (!ids.includes(trdarCd)) {
        ids.push(trdarCd);
    }
    cmpSave(ids);
}

// 로그인/구독 상태(/api/members/me)를 페이지당 한 번만 조회해 재사용한다.
let _mePromise = null;
function getMe() {
    if (!_mePromise) {
        _mePromise = fetch("/api/members/me", { credentials: "include", headers: { Accept: "application/json" } })
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (b) { return (b && b.data) || null; })
            .catch(function () { return null; });
    }
    return _mePromise;
}

// 비교는 Pro 전용. Pro면 비교함에 담고, 아니면 담지 않고 비교 페이지(게이트)로 보낸다.
function goCompareWith(trdarCd) {
    getMe().then(function (me) {
        if (me && me.pro) {
            cmpAdd(trdarCd);
        }
        location.href = "/map/compare";
    });
}

// 비Pro면 비교 담기 버튼에 Pro 전용임을 표시한다(클릭 동작은 goCompareWith가 게이트로 보낸다).
// showBadge: 텍스트 버튼은 "Pro" 배지, 아이콘 버튼(.sel-add)은 좁아 툴팁만 단다.
function markCompareLock(el, showBadge) {
    if (!el) {
        return;
    }
    getMe().then(function (me) {
        if (me && me.pro) {
            return;
        }
        el.setAttribute("title", "Pro 전용");
        if (!showBadge || el.querySelector(".cmp-lock-badge")) {
            return;
        }
        var badge = document.createElement("span");
        badge.className = "cmp-lock-badge";
        badge.textContent = "Pro";
        badge.style.cssText = "margin-left:6px;padding:1px 6px;border-radius:999px;"
            + "background:#f7e9e3;color:#862f1e;font-size:11px;font-weight:700;vertical-align:middle";
        el.appendChild(badge);
    });
}

// 매출 rows를 분기 오름차순 합계 [{q, amt}]로
function quarterlyTotals(rows) {
    const byQ = new Map();
    rows.forEach((r) => {
        byQ.set(r.stdrYyquCd, (byQ.get(r.stdrYyquCd) || 0) + (r.thsmonSelngAmt || 0));
    });
    return [...byQ.entries()].sort((a, b) => (a[0] < b[0] ? -1 : 1))
        .map(([q, amt]) => ({ q: q, amt: amt }));
}

// 값 배열을 680x150 SVG 꺾은선 좌표로
function trendPoints(values) {
    const min = Math.min(...values);
    const max = Math.max(...values);
    const span = max - min || 1;
    const stepX = values.length > 1 ? (672 - 8) / (values.length - 1) : 0;
    return values.map((v, i) => ({
        x: +(8 + stepX * i).toFixed(1),
        y: +(142 - ((v - min) / span) * 134).toFixed(1),
    }));
}

// 두 좌표 사이 직선거리 km
function distanceKm(lat1, lon1, lat2, lon2) {
    const rad = Math.PI / 180;
    const dLat = (lat2 - lat1) * rad;
    const dLon = (lon2 - lon1) * rad;
    const a = Math.sin(dLat / 2) ** 2 +
        Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLon / 2) ** 2;
    return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

// 헤더 최근검색 드롭다운(포커스 시). onSelect로 클릭 동작 주입, search.js는 자체 재검색이라 미사용
function initRecentSearch(onSelect) {
    const wrap = document.querySelector(".app-search-wrap");
    const input = document.querySelector(".app-search input");
    const box = document.getElementById("recent-box");
    if (!wrap || !input || !box) { return; }

    const pick = onSelect || function (kw) {
        location.href = "/map/search?q=" + encodeURIComponent(kw);
    };
    let recentList = [];

    function renderRecent(list) {
        box.innerHTML = "";
        if (!list.length) { box.hidden = true; return; }
        list.forEach((r) => {
            const item = document.createElement("div");
            item.className = "recent-item";
            const kw = document.createElement("span");
            kw.textContent = r.keyword; // 사용자 입력이라 textContent로만(XSS 방지)
            const del = document.createElement("button");
            del.type = "button";
            del.className = "recent-item__del";
            del.textContent = "×";
            del.setAttribute("aria-label", "삭제");
            item.appendChild(kw);
            item.appendChild(del);
            item.addEventListener("click", () => {
                box.hidden = true;
                pick(r.keyword);
            });
            del.addEventListener("click", (e) => {
                e.stopPropagation();
                fetch("/api/search-logs/" + encodeURIComponent(r.keyword), {
                    method: "DELETE",
                    credentials: "include",
                }).then(loadRecent).catch(() => {});
            });
            box.appendChild(item);
        });
        box.hidden = false;
    }

    // 입력 중이면 최신 3개, 빈 창이면 5개
    function showRecent() {
        renderRecent(recentList.slice(0, input.value.trim() ? 3 : 5));
    }

    function loadRecent() {
        apiData("/api/search-logs?limit=5")
            .then((list) => { recentList = list || []; showRecent(); })
            .catch(() => { box.hidden = true; });
    }

    input.addEventListener("focus", loadRecent);
    input.addEventListener("input", showRecent);
    document.addEventListener("click", (e) => {
        if (!wrap.contains(e.target)) { box.hidden = true; }
    });
}

// 로그인 세션을 헤더 사용자 영역(.app-user)에 반영한다. 모든 지도 페이지 공통.
(function () {
    function esc(s) {
        var d = document.createElement("div");
        d.textContent = s == null ? "" : String(s);
        return d.innerHTML;
    }
    document.addEventListener("DOMContentLoaded", function () {
        var el = document.querySelector(".app-user");
        if (!el) { return; }
        getMe().then(function (me) {
            if (me) {
                var initial = (me.nickname || me.loginId || "?").slice(0, 1);
                el.setAttribute("href", "/member/mypage");
                el.setAttribute("title", "마이페이지");
                el.innerHTML = '<span class="app-avatar" aria-hidden="true">' + esc(initial) + "</span>"
                    + esc(me.nickname || me.loginId);
            } else {
                el.setAttribute("href", "/member/login");
                el.innerHTML = '<span class="app-avatar" aria-hidden="true">?</span>로그인';
            }
        });
    });
})();
