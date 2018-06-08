/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tez.dag.api.client;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.util.SystemClock;
import org.apache.tez.client.TezAppMasterStatus;
import org.apache.tez.dag.api.TezException;
import org.apache.tez.dag.api.records.DAGProtos.DAGPlan;
import org.apache.tez.dag.app.AppContext;
import org.apache.tez.dag.app.DAGAppMaster;
import org.apache.tez.dag.app.DAGAppMasterState;
import org.apache.tez.dag.app.dag.DAG;
import org.apache.tez.dag.records.TezDAGID;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.internal.util.collections.Sets;


public class TestDAGClientHandler {

  DAG mockDAG;
  DAGClientHandler dagClientHandler;
  DAGStatusBuilder mockDagStatusBuilder;
  DAGAppMaster mockDagAM;
  VertexStatusBuilder mockVertexStatusBuilder;
  String dagIdStr = "dag_9999_0001_1";

  @Before
  public void setUp() {

    TezDAGID mockTezDAGId = mock(TezDAGID.class);
    when(mockTezDAGId.getId()).thenReturn(1);
    when(mockTezDAGId.toString()).thenReturn(dagIdStr);

    mockDAG = mock(DAG.class);
    when(mockDAG.getID()).thenReturn(mockTezDAGId);
    mockDagStatusBuilder = mock(DAGStatusBuilder.class);
    when(mockDAG.getDAGStatus(anySetOf(StatusGetOpts.class))).thenReturn(
        mockDagStatusBuilder);
    mockVertexStatusBuilder =
        mock(VertexStatusBuilder.class);
    when(mockDAG.getVertexStatus(anyString(), anySetOf(StatusGetOpts.class)))
        .thenReturn(mockVertexStatusBuilder);

    mockDagAM = mock(DAGAppMaster.class);
    when(mockDagAM.getState()).thenReturn(DAGAppMasterState.RUNNING);
    AppContext mockAppContext = mock(AppContext.class);
    when(mockDagAM.getContext()).thenReturn(mockAppContext);
    when(mockDagAM.getContext().getCurrentDAG()).thenReturn(mockDAG);
    when(mockAppContext.getClock()).thenReturn(new SystemClock());

    dagClientHandler = new DAGClientHandler(mockDagAM);
  }

  @Test(timeout = 5000)
  public void testDAGClientHandler() throws TezException {
    // getAllDAGs()
    assertEquals(1, dagClientHandler.getAllDAGs().size());
    assertEquals(dagIdStr, dagClientHandler.getAllDAGs().get(0));

    // getDAGStatus
    try {
      dagClientHandler.getDAGStatus("dag_9999_0001_2", Sets.newSet(StatusGetOpts.GET_COUNTERS));
      fail("should not come here");
    } catch (TezException e) {
      assertTrue(e.getMessage().contains("Unknown dagId"));
    }
    DAGStatus dagStatus = dagClientHandler.getDAGStatus(dagIdStr,
        Sets.newSet(StatusGetOpts.GET_COUNTERS));
    assertEquals(mockDagStatusBuilder, dagStatus);

    // getVertexStatus
    try {
      dagClientHandler.getVertexStatus("dag_9999_0001_2", "v1", Sets.newSet(StatusGetOpts.GET_COUNTERS));
      fail("should not come here");
    } catch (TezException e) {
      assertTrue(e.getMessage().contains("Unknown dagId"));
    }
    VertexStatus vertexStatus = dagClientHandler.getVertexStatus(dagIdStr, "v1",
        Sets.newSet(StatusGetOpts.GET_COUNTERS));
    assertEquals(mockVertexStatusBuilder, vertexStatus);
    
    
    // getTezAppMasterStatus
    when(mockDagAM.isSession()).thenReturn(false);

    assertEquals(TezAppMasterStatus.RUNNING, dagClientHandler.getTezAppMasterStatus());

    when(mockDagAM.isSession()).thenReturn(true);
    when(mockDagAM.getState()).thenReturn(DAGAppMasterState.INITED);
    assertEquals(TezAppMasterStatus.INITIALIZING, dagClientHandler.getTezAppMasterStatus());
    when(mockDagAM.getState()).thenReturn(DAGAppMasterState.ERROR);
    assertEquals(TezAppMasterStatus.SHUTDOWN, dagClientHandler.getTezAppMasterStatus());
        
    // tryKillDAG
    try{
      dagClientHandler.tryKillDAG("dag_9999_0001_2");
      fail("should not come here");
    }catch(TezException e){
      assertTrue(e.getMessage().contains("Unknown dagId"));
    }
    dagClientHandler.tryKillDAG(dagIdStr);
    ArgumentCaptor<DAG> eventCaptor = ArgumentCaptor.forClass(DAG.class);
    verify(mockDagAM, times(1)).tryKillDAG(eventCaptor.capture(),
        contains("Sending client kill from"));
    assertEquals(1, eventCaptor.getAllValues().size());
    assertTrue(eventCaptor.getAllValues().get(0) instanceof DAG);
    assertEquals(dagIdStr,  ((DAG)eventCaptor.getAllValues().get(0)).getID().toString());

    // submitDAG
    DAGPlan dagPlan = DAGPlan.getDefaultInstance();
    Map<String,LocalResource> localResources = new HashMap<String, LocalResource>();
    dagClientHandler.submitDAG(dagPlan, localResources);
    verify(mockDagAM).submitDAGToAppMaster(dagPlan, localResources);
    
    // shutdown
    dagClientHandler.shutdownAM();
    verify(mockDagAM).shutdownTezAM(contains("Received message to shutdown AM from"));
  }

  @Test(timeout = 5000)
  public void testUpdateDAGCredentials() throws TezException{
    Credentials credentials = new Credentials();
    dagClientHandler.updateDAGCredentials(dagIdStr, credentials);
    verify(mockDagAM).updateDAGCredentials(credentials, mockDAG);
  }

  @Test(timeout = 5000)
  public void testUpdateAMCredentials() throws TezException{
    Credentials credentials = new Credentials();
    dagClientHandler.updateAMCredentials(credentials);
    verify(mockDagAM).updateAMCredentials(credentials);
  }
}
