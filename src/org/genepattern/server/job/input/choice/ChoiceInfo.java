package org.genepattern.server.job.input.choice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;
import org.genepattern.webservice.ParameterInfo;

/**
 * Initialized by parsing the manifest for a given parameter info.
 * @author pcarr
 *
 */
public class ChoiceInfo {
    final static private Logger log = Logger.getLogger(ChoiceInfo.class);

    /**
     * Get the status of the choiceInfo to indicate to the end-user if there were problems initializing 
     * the list of choices for the parameter. Example status messages,
     * 
     *     OK, Initialized from values attribute (old way)
     *     OK, Initialized from choices attribute (new way, not dynamic)
     *     OK, Initialized from remote server (url=, date=)
     *     WARN, Initialized from cache, problem connecting to remote server
     *     ERROR, Error in module manifest, didn't initialize choices.
     *     ERROR, Connection error to remote server (url)
     *     ERROR, Timeout waiting for listing from remote server (url, timeout)
     * 
     * @author pcarr
     *
     */
    public static class Status {
        public static enum Flag {
            OK,
            WARNING,
            ERROR
        }
        
        final private Flag flag;
        final private String message;
        
        public Status(final Flag flag) {
            this(flag,flag.toString());
        }
        public Status(final Flag flag, final String message) {
            this.flag=flag;
            this.message=message;
        }
        
        public Flag getFlag() {
            return flag;
        }
        
        public String getMessage() {
            return message;
        }
    }

    
    //final static ChoiceInfoParser defaultChoiceInfoParser= new DefaultChoiceInfoParser();
    final static ChoiceInfoParser choiceInfoParser= new DynamicChoiceInfoParser();
    public static ChoiceInfoParser getChoiceInfoParser() {
        return choiceInfoParser;
    }
    
    final private String paramName;
    
    private String ftpDir=null;
    private List<Choice> choices=null;
    private ChoiceInfo.Status status=null;
    private Choice selected=null;
    
    public ChoiceInfo(final String paramName) {
        this.paramName=paramName;
    }
    
    public String getParamName() {
        return paramName;
    }
    
    public Choice getSelected() {
        return selected;
    }
    
    public void setFtpDir(final String ftpDir) {
        this.ftpDir=ftpDir;
    }
    public String getFtpDir() {
        return ftpDir;
    }
    
    public List<Choice> getChoices() {
        if (choices==null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(choices);
    }
    
    public ChoiceInfo.Status getStatus() {
        return status;
    }
    
    public void add(final Choice choice) {
        if (choices==null) {
            choices=new ArrayList<Choice>();
        }
        choices.add(choice);
    }
    
    public void setStatus(final ChoiceInfo.Status.Flag statusFlag, final String statusMessage) {
        this.status=new ChoiceInfo.Status(statusFlag, statusMessage);
    }
    
    /**
     * If necessary add an empty item to the top of the choice list and select the default value.
     * Must call this method after the list of choices is initialized.
     * 
     * If there is an empty valued item on the list don't create a new 'Choose...' entry.
     * Otherwise,
     *     If the param is optional, always add a 'Choose...' option as the 1st item on the list
     *     If the param is required, include a 'Choose...' option only if there is no default value
     * 
     * @param defaultValue
     */
    public void initDefaultValue(final ParameterInfo param) {
        if (param==null) {
            throw new IllegalArgumentException("param==null");
        }
        final String defaultValue=param.getDefaultValue();
        final boolean isOptional=param.isOptional();
        final boolean hasDefaultValue = ChoiceInfoHelper.isSet(defaultValue);
        
        //1) check for an existing empty valued item 
        Choice emptyChoice = getFirstMatchingValue("");
        if (emptyChoice == null) {
            if (isOptional || (!isOptional && !hasDefaultValue )) {
                //check manifest for displayValue for the first item on the list
                String emptyDisplayValue= (String) param.getAttributes().get("emptyDisplayValue");
                if (emptyDisplayValue==null) {
                    emptyDisplayValue="Choose...";
                }
                emptyChoice=new Choice(emptyDisplayValue, "");
                if (choices==null) {
                    log.error("Can't set default value on an empty list");
                }
                else {
                    //insert at top of the list
                    choices.add(0, emptyChoice);
                } 
            }
        }
        
        //2) select either the default value or the empty value if there is no default value
        Choice defaultChoice;
        if (!hasDefaultValue) {
            defaultChoice=emptyChoice;
        }
        else {
            // if we're here, it means there is a default value
            defaultChoice = getFirstMatchingValue(defaultValue);
            if (defaultChoice==null) {
                //try to match by name
                defaultChoice = getFirstMatchingLabel(defaultValue);
                if (defaultChoice != null) {
                    log.debug("No match by value, but found match by displayName="+defaultValue);
                }
            }
            if (defaultChoice==null) {
                log.debug("No match in choice for defaultValue="+defaultValue);
                defaultChoice=emptyChoice;
            }
        }
        selected=defaultChoice;
        log.debug("Initial selection is "+selected);
    }
    
    private Choice getFirstMatchingValue(final String value) {
        if (value==null) {
            throw new IllegalArgumentException("value==null");
        }
        if (choices==null) {
            return null;
        }
        for(Choice choice : choices) {
            if (value.equals(choice.getValue())) {
                return choice;
            }
        }
        return null;
    }

    private Choice getFirstMatchingLabel(final String label) {
        if (label==null) {
            throw new IllegalArgumentException("label==null");
        }
        if (choices==null) {
            return null;
        }
        for(Choice choice : choices) {
            if (label.equals(choice.getLabel())) {
                return choice;
            }
        }
        return null;
    }

}
