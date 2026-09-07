console.log("FastDrop Renderer Loaded");
if (window.fastdrop) {
  window.fastdrop.getPeers().then(peers => console.log("Peers:", peers)).catch(() => {});
}
