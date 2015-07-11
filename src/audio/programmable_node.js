export default class ProgrammableNode {
	constructor(context) {
		this.context = context;
		this.node = context.createScriptProcessor(0, 1, 1);
		this._renderCallback = null;
	}

	renderCallback(value) {
		if (value === undefined) {
			return this._renderCallback;
		}
		else {
			this._renderCallback = value;
			this.node.onaudioprocess = value;
		}
	}

	connect(destination, output, input) {
		this.node.connect(destination, output, input);
	}

	disconnect(destination, output, input) {
		this.node.disconnect(destination, output, input);
	}
}
