const axios = require('axios');

async function run() {
  try {
    const loginRes = await axios.post('http://localhost:8080/api/v1/auth/login', {
      email: 'test@example.com',
      password: 'password123'
    });
    const token = loginRes.data.accessToken;
    console.log("Logged in!");

    const queueRes = await axios.get('http://localhost:8080/api/v1/queue/1001/pass', {
      headers: {
        Authorization: `Bearer ${token}`
      }
    });
    console.log("Queue Pass:", queueRes.data);
  } catch (e) {
    console.error("Error:", e.response ? e.response.status : e.message);
  }
}
run();
