var exec = require('cordova/exec');

function call(action, options, success, error) {
  if (typeof success === 'function' || typeof error === 'function') {
    exec(success, error, 'FileMediastore', action, [options || {}]);
    return;
  }

  return new Promise(function (resolve, reject) {
    exec(resolve, reject, 'FileMediastore', action, [options || {}]);
  });
}

exports.writeFile = function (options, success, error) {
  return call('writeFile', options, success, error);
};

exports.saveFile = function (options, success, error) {
  return call('saveFile', options, success, error);
};
