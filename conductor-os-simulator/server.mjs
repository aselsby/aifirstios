import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { extname, join, normalize } from "node:path";

const publicRoot = new URL("./public/", import.meta.url);
const sourceRoot = new URL("./src/", import.meta.url);
const port = Number(process.env.PORT ?? 8777);

const types = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".mjs": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8"
};

const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url ?? "/", `http://${request.headers.host}`);
    const pathname = url.pathname === "/" ? "/index.html" : url.pathname;
    const safePath = normalize(pathname).replace(/^(\.\.[/\\])+/, "");
    const isSourceModule = safePath.startsWith("/src/");
    const root = isSourceModule ? sourceRoot : publicRoot;
    const relativePath = isSourceModule ? safePath.replace(/^\/src\//, "/") : safePath;
    const fileUrl = new URL(`.${relativePath}`, root);

    if (!fileUrl.href.startsWith(root.href)) {
      response.writeHead(403);
      response.end("Forbidden");
      return;
    }

    const body = await readFile(fileUrl);
    response.writeHead(200, { "content-type": types[extname(fileUrl.pathname)] ?? "application/octet-stream" });
    response.end(body);
  } catch {
    response.writeHead(404);
    response.end("Not found");
  }
});

server.listen(port, "127.0.0.1", () => {
  console.log(`Conductor OS simulator listening at http://127.0.0.1:${port}`);
});
