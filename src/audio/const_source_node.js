export default class ConstSourceNode {
	constructor(context) {
		this.buffer = context.createBuffer(1, 1, context.sampleRate);

		this.node = context.createBufferSource();
		this.node.buffer = this.buffer;
		this.node.loop = true;
		this.node.start();
	}

	value(value) {
		if (value === undefined) {
			return this.buffer.getChannelData(0)[0];
		}
		else {
			this.buffer.getChannelData(0)[0] = value;
		}
	}

	connect(destination, output, input) {
		if (output === 0) {
			this.node.connect(destination, input);
		}
	}

	disconnect(destination, output, input) {
		if (output === 0) {
			this.node.disconnect(destination, input);
		}
	}
}
