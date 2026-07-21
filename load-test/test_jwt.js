import crypto from 'k6/crypto';
import encoding from 'k6/encoding';

export default function() {
    const header = encoding.b64encode(JSON.stringify({ alg: 'HS256', typ: 'JWT' }), 'rawurl');
    const b64Payload = encoding.b64encode(JSON.stringify({ sub: '123' }), 'rawurl');
    const signature = crypto.hmac('sha256', 'secret', header + '.' + b64Payload, 'base64rawurl');
    console.log(header + '.' + b64Payload + '.' + signature);
}
