package api;

import graphs.VertexID;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import system.Edge;
import system.Message;

/**
 * Represents the pair object containing partitionID and vertexIdentifier
 * 
 * @author Prakash Chandrasekaran
 * @author Gautham Narayanasamy
 * @author Vijayaraghavan Subbaiah
 * 
 */
public abstract class Vertex implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2036651815090314092L;
	private VertexID vertexID;
	private List<Edge> outgoingEdges;
	private Data<?> data;
	private long superstep;
	/**
	 * 
	 * @param vertexID
	 *            Represents the pair object containing partitionID and
	 *            vertexIdentifier
	 * @param outgoingEdges
	 *            Represents the list of outgoing edges from the source vertex
	 */
	protected Vertex(VertexID vertexID, List<Edge> outgoingEdges) throws RemoteException{
		this.vertexID = vertexID;
		this.outgoingEdges = outgoingEdges;
	}

	/**
	 * Gets the vertex identifier
	 * 
	 * @return Returns the vertex identifier
	 */
	public VertexID getID() {
		return vertexID;
	}

	/**
	 * Gets the list of outgoing edges for this source vertex
	 * 
	 * @return Returns the list of outgoing edges for this source vertex
	 */
	public List<Edge> getOutgoingEdges() {
		return outgoingEdges;
	}

	/**
	 * returns String representation of the Vertex 
	 */
	public String toString() {
		return "(" + vertexID + "{" + data + "}" + "-" + outgoingEdges + ")";
	}
	
	/**
	 * abstract compute method, When a vertex is active, it executes it compute method
	 * by taking all input messages and sends message to all its outgoing edges
	 * @param iterator, iterator of messages
	 * @return
	 */
	public abstract Map<VertexID, Message> compute(Iterator<Message> iterator) throws RemoteException;

	/**
	 * gets Data associated with the vertex
	 * @return
	 */
	public Data<?> getData(){
		return data;
	}
	
	/**
	 * sets Data associated with the Vertex
	 * @param data
	 */
	public void setData(Data<?> data){
		this.data = data;
	}
	
	/**
	 * returns SuperStep in which the Vertex is executing 
	 * @return
	 */
	public long getSuperstep(){
		return superstep;
	}
	
	/**
	 * sets the SuperStep value for current Vertex
	 * @param superstepCounter
	 */
	public void setSuperstep(long superstepCounter){
		this.superstep = superstepCounter;
	}
}
