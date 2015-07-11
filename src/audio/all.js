import ADSRNode from './adsr_node';
import BufferSourceWrapperNode from './buffer_source_wrapper_node';
import ConstSourceNode from './const_source_node';
import JSSourceNode from './js_source_node';
import MIDICCNode from './midi_cc_node';
import MIDIDispatch from './midi_dispatch';
import MIDINoteNode from './midi_note_node';
import ProgrammableNode from './programmable_node';
import URLBufferNode from './url_buffer_node';
import UserMediaNode from './user_media_node';

var Alder = {
	ADSRNode,
	BufferSourceWrapperNode,
	ConstSourceNode,
	JSSourceNode,
	MIDICCNode,
	MIDIDispatch,
	MIDINoteNode,
	ProgrammableNode,
	URLBufferNode,
	UserMediaNode
};

window.Alder = Alder;

export default Alder
