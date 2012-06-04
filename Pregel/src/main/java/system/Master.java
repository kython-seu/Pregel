package system;

import java.io.IOException;
import java.rmi.AccessException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import utility.GeneralUtils;
import api.Client2Master;
import api.Data;
import exceptions.PropertyNotFoundException;
import graphs.GraphPartitioner;
import graphs.VertexID;


/**
 * The Class Master.
 * 
 * @author Prakash Chandrasekaran
 * @author Gautham Narayanasamy
 * @author Vijayaraghavan Subbaiah
 */
public class Master extends UnicastRemoteObject implements Worker2Master, Client2Master {

	/** The master thread. */
	// private Thread masterThread;

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = -4329526430075226361L;

	/** The Constant SERVICE_NAME. */
	private static final String SERVICE_NAME = "Master";

	/** The worker id. */
	// private static int workerID = 0;

	/** The total number of worker threads. */
	private static AtomicInteger totalWorkerThreads = new AtomicInteger(0);

	/** Superstep Counter *. */
	private long superstepCounter;

	/** The workerID to WorkerProxy map. */
	Map<String, WorkerProxy> workerProxyMap = new HashMap<>();

	/** The workerID to Worker map. **/
	Map<String, Worker> workerMap = new HashMap<>();

	/** The partitionID to workerID map. **/
	Map<Integer, String> partitionWorkerMap;

	/** Set of Workers maintained for acknowledgment. */
	Set<String> workerAcknowledgementSet = new HashSet<>();
	
	/** Set of workers who will be active in the next superstep. */
	Set<String> activeWorkerSet = new HashSet<>();
	
	/**
	 * Instantiates a new master.
	 *
	 * @throws RemoteException the remote exception
	 * @throws PropertyNotFoundException the property not found exception
	 */
	public Master() throws RemoteException, PropertyNotFoundException {
		super();
		superstepCounter = 0;
	}

	/**
	 * Registers the worker computation nodes with the master.
	 *
	 * @param worker Represents the {@link system.Worker Worker}
	 * @param workerID the worker id
	 * @param numWorkerThreads Represents the number of worker threads available in the
	 * worker computation node
	 * @return worker2 master
	 * @throws AccessException the access exception
	 * @throws RemoteException the remote exception
	 */
	public Worker2Master register(Worker worker, String workerID,
			int numWorkerThreads) throws AccessException, RemoteException {
		totalWorkerThreads.getAndAdd(numWorkerThreads);
		WorkerProxy workerProxy = new WorkerProxy(worker, workerID,
				numWorkerThreads, this);
		workerProxyMap.put(workerID, workerProxy);
		workerMap.put(workerID, worker);		
		return (Worker2Master) UnicastRemoteObject.exportObject(workerProxy, 0);
	}

	
	/* (non-Javadoc)
	 * @see api.Client2Master#putTask(java.lang.String, java.lang.String)
	 */
	@Override
	public <T> void putTask(String graphFileName, String vertexClassName, long sourceVertexID, Data<T> initData) throws RemoteException{
		try {
			GraphPartitioner graphPartitioner = new GraphPartitioner(graphFileName, vertexClassName);
			assignPartitions(graphPartitioner, sourceVertexID, initData);
			sendWorkerPartitionInfo();
		} catch (NumberFormatException | IOException e) {
			e.printStackTrace();
		} catch (PropertyNotFoundException e) {		
			e.printStackTrace();
		}
	}

	/**
	 * Send worker partition info.
	 */
	private void sendWorkerPartitionInfo() {
		for (Map.Entry<String, WorkerProxy> entry : workerProxyMap.entrySet()) {
			WorkerProxy workerProxy = entry.getValue();
			workerProxy.setWorkerPartitionInfo(partitionWorkerMap, workerMap);
		}
	}

	/**
	 * Assign partitions to workers based on the number of processors (threads)
	 * that each worker has.
	 *
	 * @param <T> the generic type
	 * @param graphPartitioner the graph partitioner
	 * @param sourceVertexID the source vertex id
	 * @param initData the data
	 * @throws PropertyNotFoundException the property not found exception
	 */
	private <T> void assignPartitions(GraphPartitioner graphPartitioner, long sourceVertexID, Data<T> initData) throws PropertyNotFoundException {
		int totalPartitions = graphPartitioner.getNumPartitions();
		Iterator<Partition> iter = graphPartitioner.iterator();
		Partition partition = null;
		partitionWorkerMap = new HashMap<>();
		
		int sourceVertex_partitionID = GeneralUtils.getPartitionID(sourceVertexID);
		
		// Assign partitions to workers in the ratio of the number of worker
		// threads that each worker has.
		for (Map.Entry<String, WorkerProxy> entry : workerProxyMap.entrySet()) {
			WorkerProxy workerProxy = entry.getValue();
			int numThreads = workerProxy.getNumThreads();
			double ratio = (double) numThreads / totalWorkerThreads.get();
			int numPartitionsToAssign = (int) ratio * totalPartitions;
			List<Partition> workerPartitions = new ArrayList<>();
			for (int i = 0; i < numPartitionsToAssign; i++) {
				partition = iter.next();
				// Get the partition that has the sourceVertex, and add the worker that has the partition to the worker set from which acknowledgments will be received.
				if(partition.getPartitionID() == sourceVertex_partitionID){
					workerAcknowledgementSet.add(entry.getKey());
					
				}
				workerPartitions.add(partition);
				partitionWorkerMap.put(partition.getPartitionID(),
						workerProxy.getWorkerID());
			}
			workerProxy.addPartitionList(workerPartitions);
		}

		// Add the remaining partitions (if any) in a round-robin fashion.
		Iterator<Map.Entry<String, WorkerProxy>> workerMapIter = workerProxyMap
				.entrySet().iterator();
		while (iter.hasNext()) {
			// If the remaining partitions is greater than the number of the
			// workers, start iterating from the beginning again.
			if (!workerMapIter.hasNext()) {
				workerMapIter = workerProxyMap.entrySet().iterator();
			}
			partition = iter.next();
			
			WorkerProxy workerProxy = workerMapIter.next().getValue();
			// Get the partition that has the sourceVertex, and add the worker that has the partition to the worker set from which acknowledgments will be received.
			if(partition.getPartitionID() == sourceVertex_partitionID){
				workerAcknowledgementSet.add(workerProxy.getWorkerID());
			}
			workerProxy.addPartition(partition);
			partitionWorkerMap.put(partition.getPartitionID(),
					workerProxy.getWorkerID());
		}
		
		setInitialMessage(sourceVertex_partitionID, sourceVertexID, initData);
	}

	
	/**
	 * Sets the initial message for the Worker that has the source vertex.
	 *
	 * @param <T> the generic type
	 * @param initData the data
	 * @param sourceVertex_partitionID the source vertex partition id
	 * @param sourceVertexID the source vertex id
	 */
	private <T> void setInitialMessage(int sourceVertex_partitionID, long sourceVertexID, Data<T> initData){
		List<Message> messageList = new ArrayList<>();
		messageList.add(new Message(null, initData));
		Map<VertexID, List<Message>> map = new HashMap<>();
		VertexID sourceVertex = new VertexID(sourceVertex_partitionID, sourceVertexID);
		map.put(sourceVertex, messageList);
		ConcurrentHashMap<Integer, Map<VertexID, List<Message>>> initialMessage = new ConcurrentHashMap<>();
		initialMessage.put(sourceVertex_partitionID, map);
		workerProxyMap.get(workerAcknowledgementSet.toArray()[0]).setInitialMessage(initialMessage);
		
	}
	
	/**
	 * The main method.
	 *
	 * @param args the arguments
	 * @throws Exception the exception
	 */
	public static void main(String[] args) throws Exception {
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}
		Master master;
		try {
			master = new Master();
			Registry registry = LocateRegistry.createRegistry(1099);
			registry.rebind(Master.SERVICE_NAME, master);
			System.out.println("Master Instance is bound and ready");
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */

	/**
	 * Removes the worker.
	 *
	 * @param serviceName the service name
	 * @throws RemoteException the remote exception
	 */
	public void removeWorker(String serviceName) throws RemoteException {
		workerProxyMap.remove(serviceName);
	}

	/* (non-Javadoc)
	 * @see system.Worker2Master#superStepCompleted(java.lang.String, java.util.Set)
	 */
	@Override
	public void superStepCompleted(String workerID, Set<String> activeWorkerSet) {
		this.activeWorkerSet.addAll(activeWorkerSet);
		this.workerAcknowledgementSet.remove(workerID);
		// If the acknowledgment has been received from all the workers, start the next superstep
		if(this.workerAcknowledgementSet.size() == 0) {			
			startSuperStep(++superstepCounter);
		}
	}

	/**
	 * Start super step.
	 *
	 * @param superStepCounter the super step counter
	 */
	private void startSuperStep(long superStepCounter) {
		this.workerAcknowledgementSet.addAll(this.activeWorkerSet);
		this.activeWorkerSet.clear();
		for(String workerID : this.activeWorkerSet){
			this.workerProxyMap.get(workerID).startSuperStep(superStepCounter);
		}				
	}

	/* (non-Javadoc)
	 * @see api.Client2Master#takeResult()
	 */
	@Override
	public String takeResult() throws RemoteException {
		// TODO Auto-generated method stub
		return null;
	}

}
