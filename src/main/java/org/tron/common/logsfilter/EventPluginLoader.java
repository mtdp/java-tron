package org.tron.common.logsfilter;

import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.*;
import org.springframework.util.StringUtils;
import org.tron.common.logsfilter.trigger.BlockLogTrigger;
import org.tron.common.logsfilter.trigger.ContractEventTrigger;
import org.tron.common.logsfilter.trigger.ContractLogTrigger;
import org.tron.common.logsfilter.trigger.TransactionLogTrigger;

import java.io.File;
import java.util.List;

@Slf4j
public class EventPluginLoader {

    private static EventPluginLoader instance;

    private PluginManager pluginManager = null;

    private List<IPluginEventListener> eventListeners;

    private ObjectMapper objectMapper = new ObjectMapper();

    private String serverAddress;

    private String pluginPath;

    private List<TriggerConfig> triggerConfigList;

    public static EventPluginLoader getInstance(){
        if (Objects.isNull(instance)){
            synchronized(EventPluginLoader.class) {
                if (Objects.isNull(instance)) {
                    instance = new EventPluginLoader();
                }
            }
        }
        return instance;
    }

    public boolean start(EventPluginConfig config){
        boolean success = false;

        if (Objects.isNull(config)){
            return success;
        }

        this.pluginPath = config.getPluginPath();
        this.serverAddress = config.getServerAddress();
        this.triggerConfigList = config.getTriggerConfigList();

        if (false == startPlugin(this.pluginPath)){
            logger.error("failed to load '{}'", this.pluginPath);
            return success;
        }

        setPluginConfig();

        return true;
    }

    private void setPluginConfig(){

        if (Objects.isNull(eventListeners)){
            return;
        }

        eventListeners.forEach(listener -> {
            listener.setServerAddress(this.serverAddress);
        });

        triggerConfigList.forEach(triggerConfig -> {
            if (EventPluginConfig.BLOCK_TRIGGER_NAME.equalsIgnoreCase(triggerConfig.getTriggerName())){
                if (triggerConfig.isEnabled()){
                    setPluginTopic(EventPluginConfig.BLOCK_TRIGGER, triggerConfig.getTopic());
                }else {
                    setPluginTopic(EventPluginConfig.BLOCK_TRIGGER, "");
                }
            }
            else if (EventPluginConfig.TRANSACTION_TRIGGER_NAME.equalsIgnoreCase(triggerConfig.getTriggerName())){
                if (triggerConfig.isEnabled()){
                    setPluginTopic(EventPluginConfig.TRANSACTION_TRIGGER, triggerConfig.getTopic());
                }else {
                    setPluginTopic(EventPluginConfig.TRANSACTION_TRIGGER, "");
                }
            }
            else if (EventPluginConfig.CONTRACTEVENT_TRIGGER_NAME.equalsIgnoreCase(triggerConfig.getTriggerName())){
                if (triggerConfig.isEnabled()){
                    setPluginTopic(EventPluginConfig.CONTRACTEVENT_TRIGGER, triggerConfig.getTopic());
                }else {
                    setPluginTopic(EventPluginConfig.CONTRACTEVENT_TRIGGER, "");
                }
            }
            else if (EventPluginConfig.CONTRACTLOG_TRIGGER_NAME.equalsIgnoreCase(triggerConfig.getTriggerName())){
                if (triggerConfig.isEnabled()){
                    setPluginTopic(EventPluginConfig.CONTRACTLOG_TRIGGER, triggerConfig.getTopic());
                }else {
                    setPluginTopic(EventPluginConfig.CONTRACTLOG_TRIGGER, "");
                }
            }
        });
    }

    private void setPluginTopic(int eventType, String topic){

        eventListeners.forEach(listener -> {
            listener.setTopic(eventType, topic);
        });
    }


    public boolean startPlugin(String path){
        boolean loaded = false;
        logger.info("start loading '{}'", path);

        File pluginPath = new File(path);
        if (false == pluginPath.exists()){
            logger.error("'{}' doesn't exist", path);
            return loaded;
        }

        if (Objects.isNull(pluginManager)){

            pluginManager = new DefaultPluginManager(pluginPath.toPath()) {
                @Override
                protected CompoundPluginDescriptorFinder createPluginDescriptorFinder() {
                    return new CompoundPluginDescriptorFinder()
                            .add(new ManifestPluginDescriptorFinder());
                }
            };
        }

        if (Objects.isNull(pluginManager)){
            logger.error("PluginManager is null");
            return loaded;
        }

        String pluginID = pluginManager.loadPlugin(pluginPath.toPath());
        if (StringUtils.isEmpty(pluginID)){
            logger.error("invalid pluginID");
            return loaded;
        }

        pluginManager.startPlugins();

        eventListeners = pluginManager.getExtensions(IPluginEventListener.class);

        if (Objects.isNull(eventListeners) || eventListeners.size() == 0){
            logger.error("No eventListener is registered");
            return loaded;
        }

        loaded = true;

        logger.info("'{}' loaded", path);

        return loaded;
    }

    public void stopPlugin(){
        if (Objects.isNull(pluginManager)){
            logger.info("pluginManager is null");
            return;
        }

        pluginManager.stopPlugins();
        logger.info("eventPlugin stopped");
    }

    public void postBlockTrigger(BlockLogTrigger trigger){
        if (Objects.isNull(eventListeners))
            return;

        eventListeners.forEach(listener -> {
            listener.handleBlockEvent(toJsonString(trigger));
        });
    }

    public void postTransactionTrigger(TransactionLogTrigger trigger){
        if (Objects.isNull(eventListeners))
            return;

        eventListeners.forEach(listener -> {
            listener.handleTransactionTrigger(toJsonString(trigger));
        });
    }

    public void postContractLogTrigger(ContractLogTrigger trigger){
        if (Objects.isNull(eventListeners))
            return;

        eventListeners.forEach(listener -> {
            listener.handleContractLogTrigger(toJsonString(trigger));
        });
    }

    public void postContractEventTrigger(ContractEventTrigger trigger){
        if (Objects.isNull(eventListeners))
            return;

        eventListeners.forEach(listener -> {
            listener.handleContractEventTrigger(toJsonString(trigger));
        });
    }

    private String toJsonString(Object data){
        String jsonData = "";

        try {
            jsonData = objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            logger.error("'{}'", e);
        }

        return jsonData;
    }

    public static void main(String[] args) {

        String path = "/Users/tron/sourcecode/eventplugin/plugins/kafkaplugin/build/libs/plugin-kafka-1.0.0.zip";

        boolean loaded = EventPluginLoader.getInstance().startPlugin(path);

        if (!loaded){
            logger.error("failed to load '{}'", path);
            return;
        }

        for (int index = 0; index < 1000; ++index){
            BlockLogTrigger trigger = new BlockLogTrigger();
            trigger.setBlockHash("0X123456789A");
            trigger.setTimeStamp(System.currentTimeMillis());
            trigger.setBlockNumber(index);
            EventPluginLoader.getInstance().postBlockTrigger(trigger);
        }

        //EventPluginLoader.getInstance().stopPlugin();

        while (true){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

