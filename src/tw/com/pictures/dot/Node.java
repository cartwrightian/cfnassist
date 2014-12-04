package tw.com.pictures.dot;


public class Node extends HasAttributes {

	private String name;
	private String target = "";
	private String edgeLabel = "";
	public Node(String name) {
		this.name = name;
	}

	public void write(Recorder writer) {
		writer.write(String.format("\"%s\"",name));	
		writeAttributes(writer, false);
		writer.writeline(";");
		writeTarget(writer);
	}

	private void writeTarget(Recorder writer) {
		if (target.isEmpty()) {
			return;
		}
		
		writer.write(String.format("\"%s\"->\"%s\" ",name, target));
		if (!edgeLabel.isEmpty()) {
			writer.write(String.format(" [ label=\"%s\" ]", edgeLabel));
		}
		writer.writeline(";");	
	}

	public Node withShape(Shape shape) {
		addShape(shape);		
		return this;
	}

	public Node withLabel(String label) {
		addLabel(label);
		return this;
	}

	public Node makeInvisible() {
		setStyleInvisible();
		return this;	
	}

}
