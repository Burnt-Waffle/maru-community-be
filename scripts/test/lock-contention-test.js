import http from 'k6/http';
import { check, sleep } from 'k6';

// 테스트 설정
export const options = {
  scenarios: {
    // 시나리오 1: 게시글 본문 수정 (락을 오래 점유하는 무거운 작업 시뮬레이션)
    post_updates: {
      executor: 'constant-vus',
      vus: 2,
      duration: '30s',
      exec: 'updatePost',
    },
    // 시나리오 2: 좋아요 및 조회수 증가 (빈번하게 일어나는 가벼운 작업)
    interactions: {
      executor: 'constant-vus',
      vus: 20,
      duration: '30s',
      exec: 'interactPost',
    },
  },
  thresholds: {
    // 성공율 95% 이상이어야 함
    http_req_failed: ['rate<0.05'],
    // 인터렉션 API의 95% 응답 시간이 500ms 이하여야 함 (개선 전에는 이 수치가 높게 나올 것임)
    'http_req_duration{scenario:interactions}': ['p(95)<2000'],
  },
};

const BASE_URL = 'http://localhost:8080/api';
const POST_ID = 1; // 테스트용 게시글 ID (미리 생성되어 있어야 함)

// 1. 게시글 수정 시나리오
export function updatePost() {
  const url = `${BASE_URL}/posts/${POST_ID}`;
  const payload = JSON.stringify({
    title: 'Updated Title ' + Math.random(),
    content: 'Very long content...'.repeat(100), // 본문을 무겁게 함
    imageUrls: []
  });
  const params = {
    headers: {
      'Content-Type': 'application/json',
      // 'Authorization': 'Bearer <TOKEN>', // 인증이 필요하다면 여기에 추가
    },
  };

  const res = http.put(url, payload, params);
  check(res, { 'update success': (r) => r.status === 200 });
  sleep(1); // 1초마다 수정 시도
}

// 2. 조회 및 좋아요 시나리오
export function interactPost() {
  // 조회 (조회수 증가 로직 포함)
  const viewRes = http.get(`${BASE_URL}/posts/${POST_ID}`);
  check(viewRes, { 'view success': (r) => r.status === 200 });

  // 좋아요 토글
  const likeRes = http.post(`${BASE_URL}/posts/${POST_ID}/like`);
  check(likeRes, { 'like success': (r) => r.status === 200 });

  sleep(0.5);
}
