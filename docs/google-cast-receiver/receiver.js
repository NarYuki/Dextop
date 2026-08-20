const NAMESPACE = 'urn:x-cast:moe.n4tsu.dextop';
const context = cast.framework.CastReceiverContext.getInstance();
const playerManager = context.getPlayerManager();
const status = document.getElementById('status');

context.addCustomMessageListener(NAMESPACE, event => {
  const message = typeof event.data === 'string' ? JSON.parse(event.data) : event.data;
  if (message.type === 'status') {
    status.textContent = message.text || 'Dextop connected';
  }
});

playerManager.addEventListener(cast.framework.events.EventType.PLAYING, () => {
  status.classList.add('hidden');
});
playerManager.addEventListener(cast.framework.events.EventType.ERROR, event => {
  status.classList.remove('hidden');
  status.textContent = `Stream error: ${event?.detailedErrorCode ?? 'unknown'}`;
});

context.start({
  disableIdleTimeout: true,
  touchScreenOptimizedApp: false
});
