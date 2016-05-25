package perflab.CSVAnalyzer;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.model.Descriptor.FormException;
import hudson.remoting.Callable;
import hudson.remoting.RemoteOutputStream;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;

import javax.servlet.ServletException;
import net.sf.json.JSONObject;

/**
 * <p>
 * When a build is performed, this plugin is being executed on slave machine.
 *
 * @author Evgeny Himmelreich
 */
public class Analyzer extends Builder {

    //private final String name;
    private String csvFiles;
    private String extraKeyFilter;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    /**
     * @param csvFiles
     * @param extraKeyFilter
     */
    @DataBoundConstructor
    public Analyzer(String csvFiles, String extraKeyFilter){
        this.csvFiles = csvFiles;
        this.extraKeyFilter = extraKeyFilter;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getCsvFiles(){
        return this.csvFiles;
    }

    public String getExtraKeyFilter(){
    	return this.extraKeyFilter;
    }
    
    //TODO: https://wiki.jenkins-ci.org/display/JENKINS/Making+your+plugin+behave+in+distributed+Jenkins
    //TODO: http://ccoetech.ebay.com/tutorial-dev-jenkins-plugin-distributed-jenkins    
    private static class LauncherCallable implements Callable<String, IOException>{
    	private BuildListener listener;
		private String csvFiles;
		private String extraKeyFilter;
		
		public LauncherCallable(BuildListener listener){
    		this.listener = listener;    		
    	}
    	
  	  	private static final long serialVersionUID = 1L;

        // This code will run on the build slave
  	  	public String call() throws IOException {
        	final RemoteOutputStream ros = new RemoteOutputStream(listener.getLogger());

        	//Write on jenkins console
            ros.write("=============================================================\n".getBytes());
            ros.write(("csvFiles = " + csvFiles + "\n").getBytes());
            ros.write("=============================================================\n".getBytes());

            //Run CSV analysis code here
            Analysis analysis = new Analysis(csvFiles, csvFiles, extraKeyFilter, this.listener.getLogger());
            
            boolean okay = analysis.execute();
                        
            return String.valueOf(okay);
      	}

		@Override
		public void checkRoles(RoleChecker arg0) throws SecurityException {
			// TODO Auto-generated method stub	
		}

		public void init(String buildNumber, String workspacePath, String csvFiles, String extraKeyFilter) {
			csvFiles = interpolatePath(csvFiles, "BUILD_NUMBER", buildNumber);
			csvFiles = interpolatePath(csvFiles, "WORKSPACE", workspacePath);

	        this.csvFiles = csvFiles;
	        this.extraKeyFilter = extraKeyFilter;
        }

        private String interpolatePath(String pathToInterpolate, String pattern, String replacement) {
            String dbgMessage = "Interpolating " + pathToInterpolate + " replace " + pattern + " with " + replacement;

            String interpolatedString = pathToInterpolate;//.replaceAll("\\", "\\\\");

            interpolatedString = interpolatedString.replaceAll("%"+pattern+"%", replacement);
            interpolatedString = interpolatedString.replaceAll("\\$\\{"+pattern+"\\}", replacement);

            return interpolatedString;
        }
    }
    
    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        boolean okay = true;

        // Get a "channel" to the slave machine and run the task there
    	try {
    		LauncherCallable remoteLauncher = new LauncherCallable(listener);
    		String buildNumber =  String.valueOf(build.getNumber());
    		String workspacePath = StringEscapeUtils.escapeJava(build.getWorkspace().toString());

    		remoteLauncher.init(buildNumber, workspacePath, csvFiles, extraKeyFilter);
    		
    		String okayString = launcher.getChannel().call(remoteLauncher);    		

    		okay = Boolean.valueOf(okayString);
    		    		
    	} catch (Exception e) {
    		RuntimeException re = new RuntimeException();
    		re.initCause(e);
    		throw re;
    	}

        return okay;
    }

	// Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }
    
    public String getDisplayName(){
    	return "perflab:CSV Analyzer";
    }
    
    /**
     * Descriptor for {@link Analyzer}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/hudson/plugins/hello_world/LoadRunnerWrapperJenkins/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */
        //private int acceptedFailurePercentage;
        //private float defaultTransactionErrorValue;
    	private String csvFiles;

        /**
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "perflab:CSV Analyzer";
        }

        @Override
        public boolean configure(StaplerRequest staplerRequest, JSONObject json) throws FormException {
            // to persist global configuration information,
            // set that to properties and call save().
            //acceptedFailurePercentage = json.getInt("acceptedFailurePercentage");
            //defaultTransactionErrorValue = json.getInt("defaultTransactionErrorValue");
            save();
            return true; // indicate that everything is good so far
        }
        
        /**
         * This method returns accepted failure percentage from the global configuration
         */
/*        public int getAcceptedFailurePercentage() {
            return acceptedFailurePercentage;
        }*/

        /**
         * This method returns default Transaction Error Value from the global configuration
         */
        //public float getDefaultTransactionErrorValue() {
        //    return defaultTransactionErrorValue;
        //}


        /**
         * Performs on-the-fly validation of the form fields.
         */
/*        public FormValidation doCheckLoadRunnerBin(@QueryParameter String value) throws IOException, ServletException {
            if (StringUtils.trimToNull(value) == null || value.length() == 0){
                return FormValidation.error("LoadRunner bin folder should not be empty. For example: C:\\Program Files (x86)\\HP\\LoadRunner\\bin");
            }
            return FormValidation.ok();
        }*/
        
 /*       public FormValidation doCheckLoadRunnerScenario(@QueryParameter String value) throws IOException, ServletException {
            if (StringUtils.trimToNull(value) == null || value.length() == 0){
                return FormValidation.error("LoadRunner scenario path should not be empty. For example: C:\\scenario\\Scenario1.lrs");
            }
            
            String fileExtension = null;
            try {
                fileExtension=value.substring(value.lastIndexOf('.') + 1);
            }catch (Exception e) {
                return FormValidation.error("LoadRunner scenario must be a file!");
            }
            if (!fileExtension.equals("lrs")){
            	return FormValidation.error("LoadRunner scenario be a lrs file!");
            }
            
            return FormValidation.ok();
        }*/
    }
}

