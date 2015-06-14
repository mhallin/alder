'use strict';

var kBufferSourceWrapperPlayModeGateOnRestart = 'retrig-restart';
var kBufferSourceWrapperPlayModeGateOffStop = 'gate-off-stop';

function BufferSourceWrapperNode(context) {
	this.context = context;

	this.buffer = null;
	this.playbackRate = null;
	this.loop = null;
	this.loopStart = null;
	this.loopEnd = null;
	this.playMode = null;

	this._connections = [];
	this._node = null;
}

BufferSourceWrapperNode.prototype.connect = function (destination, output, input) {
	this._connections.push([destination, output, input]);
};

BufferSourceWrapperNode.prototype.disconnect = function (destination, output, input) {
	for (var i = 0; i < this._connections.length; ++i) {
		var connection = this._connections[i];

		if (connection[0] === destination &&
			connection[1] === output &&
			connection[2] === input) {
			this._connections.splice(i, 1);
			break;
		}
	}
};

BufferSourceWrapperNode.prototype.gate = function (value) {
	var now = this.context.currentTime;
	var oldNode = null;

	if (this._node && (value > 0 ||
					   this.playMode === kBufferSourceWrapperPlayModeGateOffStop)) {
		this._node.stop(now);
		oldNode = this._node;
		this._node = null;
	}

	if (value > 0 && this.buffer) {
		this._node = this.context.createBufferSource();
		this._node.buffer = this.buffer;
		this._node.playbackRate.value = this.playbackRate;
		this._node.loop = this.loop;
		this._node.loopStart = this.loopStart;
		this._node.loopEnd = this.loopEnd;

		for (var i = 0; i < this._connections.length; ++i) {
			var connection = this._connections[i];

			if (connection[1] === undefined && connection[2] === undefined) {
				this._node.connect(connection[0]);
			}
			else if (connection[2] === undefined) {
				this._node.connect(connection[0], connection[1]);
			}
			else {
				this._node.connect(connection[0], connection[1], connection[2]);
			}
		}

		this._node.start(now);
	}
};

module.exports = BufferSourceWrapperNode;
