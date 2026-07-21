import http from 'k6/http';
import { check, sleep } from 'k6';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';

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

function randomIp() {
    return `${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}`;
}

export const options = {
    vus: 50, // lower VUs to see what errors occur without timing out
    duration: '10s',
};

export default function () {
    const userId = uuidv4();
    const token = signAuthJWT(userId);
    const ip = randomIp();
    const params = { headers: { 'Authorization': 'Bearer ' + token, 'X-Forwarded-For': ip, 'Content-Type': 'application/json' } };
    
    const eventId = 1;
    const passRes = http.get(`http://localhost:8080/api/v1/queue/${eventId}/pass`, params);
    if (passRes.status !== 200) { console.log("Pass Error: " + passRes.status); return; }
    
    const queueToken = passRes.json('passToken');
    const queueParams = { headers: Object.assign({}, params.headers, { 'X-Queue-Pass': queueToken }) };

    const seatmapRes = http.get(`http://localhost:8080/api/v1/events/${eventId}/seatmap`, queueParams);
    if (seatmapRes.status !== 200) { console.log("Seatmap Error: " + seatmapRes.status); return; }
    
    const seats = seatmapRes.json();
    const availableSeats = seats.filter(s => s.status === 'AVAILABLE');
    if (availableSeats.length < 2) return;
    
    const s1 = availableSeats[Math.floor(Math.random() * availableSeats.length)].id;
    const s2 = availableSeats[Math.floor(Math.random() * availableSeats.length)].id;
    if (s1 === s2) return;

    const reserveRes = http.post('http://localhost:8080/api/v1/bookings', JSON.stringify({ eventId, seatIds: [s1, s2] }), queueParams);
    if (reserveRes.status !== 200 && reserveRes.status !== 201) {
        console.log("Reserve Error: " + reserveRes.status + " Body: " + reserveRes.body);
    }
}
