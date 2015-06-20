function getUserMedia(constraints, success, failure) {
	var ctor = (navigator.getUserMedia ||
				navigator.webkitGetUserMedia ||
				navigator.mozGetUserMedia ||
				navigator.msGetUserMedia);

	if (!ctor) {
		return false;
	}

	ctor.call(navigator, constraints, success, failure);

	return true;
}

export default class UserMediaNode {
	constructor(context) {
		this.context = context;

		this._node = null;
		this._connections = [];
		this._enabled = false;
	}

	connect(destination, output, input) {
		if (output === 0) {
			this._connections.push([destination, input]);

			if (this._node) {
				this._node.connect(destination, 0, input);
			}
		}
	}

	disconnect(destination, output, input) {
		if (output === 0) {
			for (var i = 0; i < this._connections.length; ++i) {
				var connection = this._connections[i];

				if (connection[0] === destination && connection[1] === input) {
					this._connections.splice(i, 1);
					break;
				}
			}

			if (this._node) {
				this._node.disconnect(destination, 0, input);
			}
		}
	}

	gate(value) {
		if (value > 0) {
			this._enabled = true;

			if (this._node) {
				this._connectNode();
			}
			else {
				this._constructNode();
			}
		}
		else {
			this._enabled = false;

			if (this._node) {
				this._disconnectNode();
			}
		}
	}

	_constructNode() {
		var result = getUserMedia({ audio: true}, (stream) => {
			this._node = this.context.createMediaStreamSource(stream);

			if (this._enabled) {
				this._connectNode();
			}
		}, (err) => {
			if (console) {
				console.error('Error getting user media', err);
			}
		});

		if (!result) {
			if (console) {
				console.warn('No user media support');
			}
		}
	}

	_disconnectNode() {
		for (var i = 0; i < this._connections.length; ++i) {
			var connection = this._connections[i];

			this._node.disconnect(connection[0], 0, connection[1]);
		}
	}

	_connectNode() {
		for (var i = 0; i < this._connections.length; ++i) {
			var connection = this._connections[i];

			this._node.connect(connection[0], 0, connection[1]);
		}
	}
}
