const { contextBridge, ipcRenderer } = require('electron');

const call = (ch) => (...args) => ipcRenderer.invoke(ch, ...args);
const listen = (ch) => (fn) => {
  const h = (_e, p) => fn(p);
  ipcRenderer.on(ch, h);
  return () => ipcRenderer.removeListener(ch, h);
};

contextBridge.exposeInMainWorld('api', {
  theme: (color) => ipcRenderer.send('win:theme', color),
  clean: { scan: call('clean:scan'), run: call('clean:run'), onProgress: listen('clean:progress'), onRunProgress: listen('clean:runProgress') },
  uninstall: {
    list: call('uninstall:list'), run: call('uninstall:run'), leftovers: call('uninstall:leftovers'),
    trash: call('uninstall:trash'), reveal: call('uninstall:reveal'),
  },
  optimize: { list: call('optimize:list'), run: call('optimize:run'), onUpdate: listen('optimize:update') },
  startup: { list: call('startup:list'), set: call('startup:set'), openSettings: call('sys:openStartupSettings') },
  analyze: {
    drives: call('analyze:drives'), pick: call('analyze:pick'), scan: call('analyze:scan'), cancel: call('analyze:cancel'),
    node: call('analyze:node'), summary: call('analyze:summary'), reveal: call('analyze:reveal'), open: call('analyze:open'),
    trash: call('analyze:trash'), onProgress: listen('analyze:progress'),
  },
  status: { get: call('status:get'), kill: call('status:kill') },
});
