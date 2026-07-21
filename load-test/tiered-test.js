import http from 'k6/http';
import { check, sleep } from 'k6';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';
import { htmlReport } from "https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js";
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const GLOBAL_AUTH_SECRET = 'ticketcraft_global_auth_secret_key_32_bytes_long_minimum!';

function uuidv4() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        var r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}

function signAuthJWT(userId) {
    const header = encoding.b64encode(JSON.stringify({ alg: 'HS256', typ: 'JWT' }), 'rawurl');
    const payload = {
        sub: userId,
        email: `loaduser_${userId}@example.com`,
        role: "USER",
        type: "access",
        jti: uuidv4(),
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600
    };
    const b64Payload = encoding.b64encode(JSON.stringify(payload), 'rawurl');
    const signature = crypto.hmac('sha256', GLOBAL_AUTH_SECRET, header + '.' + b64Payload, 'base64rawurl');
    return header + '.' + b64Payload + '.' + signature;
}

export const options = {
    scenarios: {
        tiered_test: {
            executor: 'ramping-vus',
            startVUs: 10,
            stages: [
                { duration: '10s', target: 10 },    // Smoke
                { duration: '30s', target: 100 },   // Normal
                { duration: '60s', target: 1000 },  // Peak
                { duration: '30s', target: 100 },   // Recovery
            ],
        },
    },
};

export default function () {
    const userId = uuidv4();
    const token = signAuthJWT(userId);
    const ip = `${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}`;

    const params = {
        headers: {
            'Authorization': 'Bearer ' + token,
            'X-Forwarded-For': ip,
            'Content-Type': 'application/json'
        }
    };

    const eventIds = [1, 2, 3];
    const eventId = eventIds[Math.floor(Math.random() * eventIds.length)];

    const passRes = http.get(`http://localhost:8080/api/v1/queue/${eventId}/pass`, params);
    check(passRes, { 'pass 200': (r) => r.status === 200 });

    if (passRes.status !== 200) return;
    
    const queueToken = passRes.json('passToken');
    const queueParams = {
        headers: Object.assign({}, params.headers, { 'X-Queue-Pass': queueToken })
    };

    const seatmapRes = http.get(`http://localhost:8080/api/v1/events/${eventId}/seatmap`, queueParams);
    check(seatmapRes, { 'seatmap 200': (r) => r.status === 200 });

    if (seatmapRes.status !== 200) return;

    let availableSeats = [];
    try {
        const seats = seatmapRes.json();
        availableSeats = seats.filter(s => s.status === 'AVAILABLE');
    } catch (e) {
        return;
    }

    if (availableSeats.length < 2) return;

    const s1 = availableSeats[Math.floor(Math.random() * availableSeats.length)].id;
    let s2 = availableSeats[Math.floor(Math.random() * availableSeats.length)].id;
    while (s1 === s2) {
        s2 = availableSeats[Math.floor(Math.random() * availableSeats.length)].id;
    }

    const reservePayload = JSON.stringify({
        eventId: eventId,
        seatIds: [s1, s2]
    });

    const reserveRes = http.post('http://localhost:8080/api/v1/bookings', reservePayload, queueParams);
    check(reserveRes, { 'reserve 200/201': (r) => r.status === 200 || r.status === 201 });

    if (reserveRes.status === 200 || reserveRes.status === 201) {
        let bookingId;
        try { bookingId = reserveRes.json('id'); } catch (e) {}
        if (bookingId) {
            const checkoutRes = http.post(`http://localhost:8080/api/v1/bookings/${bookingId}/checkout`, null, params);
            check(checkoutRes, { 'checkout 200': (r) => r.status === 200 });
        }
    }
    
    sleep(1);
}

export function handleSummary(data) {
    return {
        "tiered-report.html": htmlReport(data),
        stdout: textSummary(data, { indent: ' ', enableColors: false }),
    };
}
