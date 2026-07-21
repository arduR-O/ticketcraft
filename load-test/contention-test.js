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
        contention_spike: {
            executor: 'shared-iterations',
            vus: 1000,
            iterations: 1000,
            maxDuration: '30s',
        },
    },
};

export function setup() {
    // 1. Authenticate (as admin or user doesn't matter for fetching public seatmap)
    const userId = uuidv4();
    const token = signAuthJWT(userId);
    const params = {
        headers: {
            'Authorization': 'Bearer ' + token,
            'X-Forwarded-For': "1.1.1.1",
            'Content-Type': 'application/json'
        }
    };

    // 2. We need a queue pass just to access the seatmap once
    const eventId = 1;
    let queueToken = null;
    const passRes = http.get(`http://localhost:8080/api/v1/queue/${eventId}/pass`, params);
    if (passRes.status === 200) {
        queueToken = passRes.json('passToken');
    }

    if (!queueToken) {
        throw new Error("Setup failed to get queue pass");
    }

    const queueParams = {
        headers: Object.assign({}, params.headers, { 'X-Queue-Pass': queueToken })
    };

    // 3. Fetch seatmap and pick exactly 10 seats
    const seatmapRes = http.get(`http://localhost:8080/api/v1/events/${eventId}/seatmap`, queueParams);
    if (seatmapRes.status !== 200) {
        throw new Error("Setup failed to get seatmap");
    }

    const seats = seatmapRes.json();
    const availableSeats = seats.filter(s => s.status === 'AVAILABLE');
    const targetSeatIds = availableSeats.slice(0, 10).map(s => s.id);

    console.log("Targeting exact 10 seats: ", targetSeatIds);
    return { eventId, targetSeatIds };
}

export default function (data) {
    const userId = uuidv4();
    const token = signAuthJWT(userId);
    // Unique IP to bypass rate limiting across VUs
    const ip = `${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}`;

    const params = {
        headers: {
            'Authorization': 'Bearer ' + token,
            'X-Forwarded-For': ip,
            'Content-Type': 'application/json'
        }
    };

    // 1. Every VU gets their own queue pass
    const passRes = http.get(`http://localhost:8080/api/v1/queue/${data.eventId}/pass`, params);
    check(passRes, { 'pass 200': (r) => r.status === 200 });

    if (passRes.status !== 200) return;
    
    const queueToken = passRes.json('passToken');
    const queueParams = {
        headers: Object.assign({}, params.headers, { 'X-Queue-Pass': queueToken })
    };

    // 2. Try to reserve a random pair from those EXACT 10 seats
    const s1 = data.targetSeatIds[Math.floor(Math.random() * data.targetSeatIds.length)];
    let s2 = data.targetSeatIds[Math.floor(Math.random() * data.targetSeatIds.length)];
    while (s1 === s2) {
        s2 = data.targetSeatIds[Math.floor(Math.random() * data.targetSeatIds.length)];
    }

    const reservePayload = JSON.stringify({
        eventId: data.eventId,
        seatIds: [s1, s2]
    });

    const reserveRes = http.post('http://localhost:8080/api/v1/bookings', reservePayload, queueParams);
    
    // We expect most of these to be 409 Conflict, but checking 200/201 just to log successes
    check(reserveRes, { 
        'reserve 200/201': (r) => r.status === 200 || r.status === 201,
        'reserve 409 Conflict': (r) => r.status === 409 
    });

    // 3. Checkout if successful
    if (reserveRes.status === 200 || reserveRes.status === 201) {
        let bookingId;
        try { bookingId = reserveRes.json('id'); } catch(e) {}
        if (bookingId) {
            const checkoutRes = http.post(`http://localhost:8080/api/v1/bookings/${bookingId}/checkout`, null, params);
            check(checkoutRes, { 'checkout 200': (r) => r.status === 200 });
        }
    }
}

export function handleSummary(data) {
    return {
        "contention-report.html": htmlReport(data),
        stdout: textSummary(data, { indent: ' ', enableColors: false }),
    };
}
