package backtype.storm.drpc;

import backtype.storm.Config;
import backtype.storm.generated.DistributedRPCInvocations;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.IRichBolt;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.tuple.Tuple;
import backtype.storm.utils.DRPCInvocationsClient;
import backtype.storm.utils.ServiceRegistry;
import backtype.storm.utils.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.apache.thrift7.TException;
import org.json.simple.JSONValue;


public class ReturnResults implements IRichBolt {
    public static final Logger LOG = Logger.getLogger(ReturnResults.class);
    OutputCollector _collector;
    boolean local;

    Map<List, DRPCInvocationsClient> _clients = new HashMap<List, DRPCInvocationsClient>();

    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        _collector = collector;
        local = stormConf.get(Config.STORM_CLUSTER_MODE).equals("local");
    }

    public void execute(Tuple input) {
        String result = (String) input.getValue(0);
        String returnInfo = (String) input.getValue(1);
        if(returnInfo!=null) {
            Map retMap = (Map) JSONValue.parse(returnInfo);
            final String host = (String) retMap.get("host");
            final int port = Utils.getInt(retMap.get("port"));
            String id = (String) retMap.get("id");
            DistributedRPCInvocations.Iface client;
            if(local) {
                client = (DistributedRPCInvocations.Iface) ServiceRegistry.getService(host);
            } else {
                List server = new ArrayList() {{
                    add(host);
                    add(port);
                }};
            
                if(!_clients.containsKey(server)) {
                    _clients.put(server, new DRPCInvocationsClient(host, port));
                }
                client = _clients.get(server);
            }
                
            try {
                client.result(id, result);
                _collector.ack(input);
            } catch(TException e) {
                LOG.error("Failed to return results to DRPC server", e);
                _collector.fail(input);
            }
        }
    }    

    public void cleanup() {
        for(DRPCInvocationsClient c: _clients.values()) {
            c.close();
        }
    }

    public void declareOutputFields(OutputFieldsDeclarer declarer) {
    }

}
