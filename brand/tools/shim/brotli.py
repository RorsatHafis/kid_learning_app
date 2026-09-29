"""Local stand-in for the brotli module: decompresses with Node's built-in zlib."""
import subprocess

def decompress(data):
    js = "const z=require('zlib');const c=[];process.stdin.on('data',d=>c.push(d));process.stdin.on('end',()=>process.stdout.write(z.brotliDecompressSync(Buffer.concat(c))));"
    return subprocess.run(["node", "-e", js], input=data, capture_output=True, check=True).stdout
