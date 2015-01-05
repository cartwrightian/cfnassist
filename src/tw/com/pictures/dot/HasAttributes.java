package tw.com.pictures.dot;

import java.util.LinkedList;
import java.util.List;

import javax.management.InvalidApplicationException;

public class HasAttributes {

	private List<String> attributes = new LinkedList<String>();

	public HasAttributes() {
		super();
	}

	protected void writeAttributes(Recorder recorder, boolean isGraph) {
		if (attributes.isEmpty()) {
			return;
		}
		StringBuilder output = new StringBuilder();
		output.append(" ");
		for (String attrib : attributes) {
			output.append(attrib).append(" ");
			if (isGraph) {
				output.append("\n");
			}
		}
		if (isGraph) {
			recorder.write(output.toString());
		} else {
			recorder.write(String.format(" [ %s ] ", output));
		}
	}
	
	public void addLabel(String label) {
		attributes.add(String.format("label=\"%s\"", label));	
	}
	
	public void fontSize(int size) {
		attributes.add(String.format("fontsize = %s", size));
	}
	
	protected void addNoDirection() {
		attributes.add("dir=none");	
	}
	
	protected void addShape(Shape shape) throws InvalidApplicationException {
		switch (shape) {
		case Box:
			attributes.add("shape=box");
			break;
		case Diamond: 
			attributes.add("shape=diamond");
			break;
		case Octogon:
			attributes.add("shape=octagon");
			break;
		case Parallelogram:
			attributes.add("shape=parallelogram");
			break;
		case Box3d:
			attributes.add("share=Box3d");
			break;
		case Msquare:
			attributes.add("shape=Msquare");
			break;
		case InvHouse:
			attributes.add("shape=invhouse");
			break;
		default:
			throw new InvalidApplicationException("Unknown shape: " + shape.toString());
		}
	}
	
	protected void addDottedLine() {
		attributes.add("style=dotted");
	}
	
	protected void addDot() {
		attributes.add("arrowhead=dot");
	}
	
	protected void addBox() {
		attributes.add("arrowhead=box");
	}
	
	protected void addEndsAt(String elementId) {
		attributes.add(String.format("lhead=\"%s\"", elementId));	
	}
	
	protected void addBeginsAt(String elementId) {
		attributes.add(String.format("ltail=\"%s\"", elementId));
	}
	
	protected void addCompound() {
		attributes.add("compound=true");
	}

	public void setStyleInvisible() {
		attributes.add("style=invis");		
	}

}