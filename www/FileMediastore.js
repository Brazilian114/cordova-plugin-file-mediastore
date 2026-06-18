var exec = require('cordova/exec');

exports.writeFile = function (options, success, error) {
  exec(success, error, 'FileMediastore', 'writeFile', [options || {}]);
};

exports.saveFile = function (options, success, error) {
  exec(success, error, 'FileMediastore', 'saveFile', [options || {}]);
};
