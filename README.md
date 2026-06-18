# cordova-plugin-file-mediastore

Internal Cordova plugin for saving Android files through MediaStore and the system document picker.

## API

```js
cordova.plugins.fileMediastore.writeFile({
  data: base64Data,
  filename: 'document.pdf',
  folder: 'Download',
  mimeType: 'application/pdf'
}, success, error);

cordova.plugins.fileMediastore.saveFile({
  data: base64Data,
  filename: 'document.pdf',
  mimeType: 'application/pdf'
}, success, error);
```

`writeFile` saves to the public Downloads collection on Android 10+ through MediaStore.
`saveFile` opens the system document picker and writes to the URI selected by the user.
