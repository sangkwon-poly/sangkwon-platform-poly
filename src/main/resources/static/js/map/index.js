// 메인 지도: 서버에서 카카오 키를 받아 SDK를 로드하고 상권을 지도에 표시한다.

// ApiResponse 껍데기를 벗겨 data만 반환
async function apiData(path) {
    const res = await fetch(path);
    const body = await res.json();
    return body.data;
}

// 카카오 지도 SDK 동적 로드 (키를 정적 파일에 박지 않으려고 런타임에 주입)
function loadKakaoSdk(appKey) {
    return new Promise((resolve, reject) => {
        const s = document.createElement("script");
        s.src = "https://dapi.kakao.com/v2/maps/sdk.js?autoload=false&appkey=" + appKey;
        s.onload = () => kakao.maps.load(resolve);
        s.onerror = () => reject(new Error("카카오 지도 SDK 로드 실패 (키/도메인 등록 확인)"));
        document.head.appendChild(s);
    });
}

// 전체 상권을 마커로 표시. 클릭하면 상권명을 보여준다.
async function drawMarkers(map) {
    const districts = await apiData("/api/districts");
    const info = new kakao.maps.InfoWindow({ removable: true });
    districts.forEach((d) => {
        if (d.centerLat == null || d.centerLot == null) {
            return;
        }
        const marker = new kakao.maps.Marker({
            map: map,
            position: new kakao.maps.LatLng(d.centerLat, d.centerLot),
            title: d.trdarNm,
        });
        kakao.maps.event.addListener(marker, "click", () => {
            info.setContent('<div style="padding:6px 10px;font-size:13px">' + d.trdarNm + "</div>");
            info.open(map, marker);
        });
    });
}

// 상권 경계(폴리곤). 전량은 무거워 관광특구만 예시로 그린다.
async function drawBoundaries(map) {
    const geos = await apiData("/api/districts/geo?trdarSeCd=U");
    geos.forEach((g) => {
        if (!g.geoJson) {
            return;
        }
        const geo = JSON.parse(g.geoJson);
        // Polygon은 [ring...], MultiPolygon은 [[ring...]...] 구조라 통일한다.
        const polygons = geo.type === "MultiPolygon" ? geo.coordinates : [geo.coordinates];
        polygons.forEach((poly) => {
            const outer = poly[0]; // 외곽 링만 사용
            const path = outer.map(([lng, lat]) => new kakao.maps.LatLng(lat, lng));
            new kakao.maps.Polygon({
                map: map,
                path: path,
                strokeWeight: 2,
                strokeColor: "#a9432e",
                strokeOpacity: 0.9,
                fillColor: "#c16951",
                fillOpacity: 0.25,
            });
        });
    });
}

async function init() {
    const cfg = await apiData("/api/map/config");
    await loadKakaoSdk(cfg.kakaoJsKey);

    const mapDiv = document.getElementById("map");
    mapDiv.innerHTML = ""; // 목업 장식 제거
    mapDiv.removeAttribute("aria-hidden");
    const map = new kakao.maps.Map(mapDiv, {
        center: new kakao.maps.LatLng(37.5665, 126.978), // 서울시청
        level: 8,
    });

    await drawMarkers(map);
    await drawBoundaries(map);
}

init().catch((err) => console.error("지도 초기화 실패:", err));
