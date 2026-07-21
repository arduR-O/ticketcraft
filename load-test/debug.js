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

export default function () {
    const userId = uuidv4();
    const token = signAuthJWT(userId);
    const params = {
        headers: {
            'Authorization': 'Bearer ' + token,
            'X-Forwarded-For': "1.2.3.4",
            'Content-Type': 'application/json'
        }
    };
    
    const passRes = http.get(`http://localhost:8080/api/v1/queue/1/pass`, params);
    console.log("Pass status: " + passRes.status);
    console.log("Pass body: " + passRes.body);
}
