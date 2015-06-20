export default class URLBufferNode {
	constructor(context) {
		this.context = context;

		this._connectedNodes = [];
		this._url = null;
		this._buffer = null;
		this._currentRequest = null;
	}

	url(value) {
		if (value === undefined) {
			return this._url;
		}
		else {
			this._url = value;
			this.downloadBuffer();
		}
	}

	connect(destination, output, inputName) {
		if (output === 0) {
			this._connectedNodes.push([destination, inputName]);

			if (this._buffer) {
				destination[inputName] = this._buffer;
			}
		}
	}

	disconnect(destination, output, inputName) {
		if (output === 0) {
			for (var i = 0; i < this._connectedNodes.length; ++i) {
				var connection = this._connectedNodes[i];

				if (connection[0] === destination && connection[1] === inputName) {
					this._connectedNodes.splice(i, 1);
					break;
				}
			}
		}
	}

	downloadBuffer() {
		if (this._currentRequest) {
			this._currentRequest.abort();
			this._currentRequest = null;
		}

		if (this._url) {
			var req = new XMLHttpRequest();
			var url = this._url;

			req.onload = (e) => {
				if (req.status < 400 && this._currentRequest === req) {
					if (console.log) {
						console.log('Download successful', url, req.status);
					}
					this.decodeArrayBuffer(req);
				}
				else if (req.status >= 400) {
					if (console.log) {
						console.log('Download failed', req);
					}
				}
			};

			req.open('GET', url, true);
			req.responseType = 'arraybuffer';
			req.send();

			this._currentRequest = req;
		}
	}

	decodeArrayBuffer(req) {
		this.context.decodeAudioData(req.response, (audioBuffer) => {
			if (this._currentRequest === req) {
				this.setAudioBuffer(audioBuffer);
			}
		});
	}

	setAudioBuffer(audioBuffer) {
		this._buffer = audioBuffer;

		for (var i = 0; i < this._connectedNodes.length; ++i) {
			var node = this._connectedNodes[i];

			node.buffer = this._buffer;
		}
	}

}
