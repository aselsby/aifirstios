import http from "node:http";
import { createRealtimeTokenHttpHandler, RealtimeTokenService } from "./token-service.mjs";

const port = Number(process.env.PORT || 8787);
const service = new RealtimeTokenService();
const server = http.createServer(createRealtimeTokenHttpHandler({ service }));

server.listen(port, "127.0.0.1", () => {
  console.log(`Conductor realtime token service listening on http://127.0.0.1:${port}`);
});
