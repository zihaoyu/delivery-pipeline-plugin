/*
This file is part of Delivery Pipeline Plugin.

Delivery Pipeline Plugin is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Delivery Pipeline Plugin is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Delivery Pipeline Plugin.
If not, see <http://www.gnu.org/licenses/>.
*/
package se.diabol.jenkins.pipeline;

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.model.*;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.BadCredentialsException;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.bind.JavaScriptMethod;
import org.kohsuke.stapler.export.Exported;
import se.diabol.jenkins.pipeline.domain.Component;
import se.diabol.jenkins.pipeline.domain.Pipeline;
import se.diabol.jenkins.pipeline.trigger.ManualTrigger;
import se.diabol.jenkins.pipeline.domain.PipelineException;
import se.diabol.jenkins.pipeline.sort.ComponentComparator;
import se.diabol.jenkins.pipeline.sort.ComponentComparatorDescriptor;
import se.diabol.jenkins.pipeline.trigger.ManualTriggerFactory;
import se.diabol.jenkins.pipeline.trigger.TriggerException;
import se.diabol.jenkins.pipeline.util.PipelineUtils;
import se.diabol.jenkins.pipeline.util.ProjectUtil;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@SuppressWarnings("UnusedDeclaration")
public class DeliveryPipelineView extends View {

    private static final Logger LOG = Logger.getLogger(DeliveryPipelineView.class.getName());

    public static final int DEFAULT_INTERVAL = 2;

    public static final int DEFAULT_NO_OF_PIPELINES = 3;
    private static final int MAX_NO_OF_PIPELINES = 10;

    private static final String OLD_NONE_SORTER = "se.diabol.jenkins.pipeline.sort.NoOpComparator";
    public static final String NONE_SORTER = "none";

    private List<ComponentSpec> componentSpecs;
    private int noOfPipelines = DEFAULT_NO_OF_PIPELINES;
    private boolean showAggregatedPipeline = false;
    private int noOfColumns = 1;
    private String sorting = NONE_SORTER;
    private String fullScreenCss = null;
    private String embeddedCss = null;
    private boolean showAvatars = false;
    private int updateInterval = DEFAULT_INTERVAL;
    private boolean showChanges = false;
    private boolean allowManualTriggers = false;

    private List<RegExpSpec> regexpFirstJobs;

    private transient String error;

    @DataBoundConstructor
    public DeliveryPipelineView(String name) {
        super(name);
    }

    public DeliveryPipelineView(String name, ViewGroup owner) {
        super(name, owner);
    }

    public List<RegExpSpec> getRegexpFirstJobs() {
        return regexpFirstJobs;
    }

    public void setRegexpFirstJobs(List<RegExpSpec> regexpFirstJobs) {
        this.regexpFirstJobs = regexpFirstJobs;
    }

    public boolean getShowAvatars() {
        return showAvatars;
    }

    public void setShowAvatars(boolean showAvatars) {
        this.showAvatars = showAvatars;
    }

    public String getSorting() {
        /* Removed se.diabol.jenkins.pipeline.sort.NoOpComparator since it in some cases did sorting*/
        if (OLD_NONE_SORTER.equals(sorting)) {
            this.sorting = NONE_SORTER;
        }
        return sorting;
    }

    public void setSorting(String sorting) {
        /* Removed se.diabol.jenkins.pipeline.sort.NoOpComparator since it in some cases did sorting*/
        if (OLD_NONE_SORTER.equals(sorting)) {
            this.sorting = NONE_SORTER;
        } else {
            this.sorting = sorting;
        }
    }



    public List<ComponentSpec> getComponentSpecs() {
        return componentSpecs;
    }

    public void setComponentSpecs(List<ComponentSpec> componentSpecs) {
        this.componentSpecs = componentSpecs;
    }

    public int getNoOfPipelines() {
        return noOfPipelines;
    }

    public boolean isShowAggregatedPipeline() {
        return showAggregatedPipeline;
    }

    public void setNoOfPipelines(int noOfPipelines) {
        this.noOfPipelines = noOfPipelines;
    }

    public boolean isShowChanges() {
        return showChanges;
    }

    public void setShowChanges(boolean showChanges) {
        this.showChanges = showChanges;
    }

    public void setShowAggregatedPipeline(boolean showAggregatedPipeline) {
        this.showAggregatedPipeline = showAggregatedPipeline;
    }

    @Exported
    public boolean isAllowManualTriggers() {
        return allowManualTriggers;
    }

    public void setAllowManualTriggers(boolean allowManualTriggers) {
        this.allowManualTriggers = allowManualTriggers;
    }

    public int getNoOfColumns() {
        return noOfColumns;
    }

    public void setNoOfColumns(int noOfColumns) {
        this.noOfColumns = noOfColumns;
    }

    public String getFullScreenCss() {
        return fullScreenCss;
    }

    public int getUpdateInterval() {
        //This occurs when the plugin has been updated and as long as the view has not been updated
        //Jenkins will set the default value to 0
        if (updateInterval == 0) {
            updateInterval = DEFAULT_INTERVAL;
        }

        return updateInterval;
    }

    public void setUpdateInterval(int updateInterval) {
        this.updateInterval = updateInterval;
    }

    public void setFullScreenCss(String fullScreenCss) {
        if (fullScreenCss != null && "".equals(fullScreenCss.trim())) {
            this.fullScreenCss = null;
        } else {
            this.fullScreenCss = fullScreenCss;
        }
    }

    public String getEmbeddedCss() {
        return embeddedCss;
    }

    public void setEmbeddedCss(String embeddedCss) {
        if (embeddedCss != null && "".equals(embeddedCss.trim())) {
            this.embeddedCss = null;
        } else {
            this.embeddedCss = embeddedCss;
        }
    }

    @Override
    public void onJobRenamed(Item item, String oldName, String newName) {
        if (componentSpecs != null) {
            Iterator<ComponentSpec> it = componentSpecs.iterator();
            while (it.hasNext()) {
                ComponentSpec componentSpec = it.next();
                if (componentSpec.getFirstJob().equals(oldName)) {
                    if (newName == null) {
                        it.remove();
                    } else {
                        componentSpec.setFirstJob(newName);
                    }
                }
            }
        }
    }

    // generate the base url for calls to getApi()
    // support being the default view in Jenkins
    public String getApiBaseUrl() {
        return Jenkins.getInstance().getRootUrlFromRequest() + getViewUrl();
    }
    
    @Override
    public Api getApi() {
        return new PipelineApi(this);
    }

    @Exported
    public String getLastUpdated() {
        return PipelineUtils.formatTimestamp(System.currentTimeMillis());
    }

    @Exported
    public String getError() {
        return error;
    }

    @JavaScriptMethod
    public void startJob(String job) {
        AbstractProject project = ProjectUtil.getProject(job, getOwnerItemGroup());
        project.scheduleBuild(0, new Cause.UserIdCause());
    }

    @JavaScriptMethod
    public void triggerManual(String projectName, String upstreamName, String buildId) throws TriggerException, AuthenticationException {
        try {
            LOG.fine("Trigger manual build " + projectName + " " + upstreamName + " " + buildId);
            AbstractProject project = ProjectUtil.getProject(projectName, Jenkins.getInstance());
            if (!project.hasPermission(Item.BUILD)) {
                throw new BadCredentialsException("Not auth to build");
            }
            AbstractProject upstream = ProjectUtil.getProject(upstreamName, Jenkins.getInstance());
            ManualTrigger trigger = ManualTriggerFactory.getManualTrigger(project, upstream);
            if (trigger != null) {
                trigger.triggerManual(project, upstream, buildId, getOwner().getItemGroup());
            } else {
                String message = "Trigger not found for manual build " + projectName + " for upstream " +
                                        upstreamName + " id: " + buildId;
                LOG.log(Level.WARNING, message);
                throw new TriggerException(message);
            }
        } catch (TriggerException e) {
            LOG.log(Level.WARNING, "Could not trigger manual build " + projectName + " for upstream " +
                    upstreamName + " id: " + buildId, e);
            throw e;
        }
    }


    @Exported
    public List<Component> getPipelines() {
        try {
            LOG.fine("Getting pipelines!");
            List<Component> components = new ArrayList<Component>();
            if (componentSpecs != null) {
                for (ComponentSpec componentSpec : componentSpecs) {
                    AbstractProject firstJob = ProjectUtil.getProject(componentSpec.getFirstJob(), getOwnerItemGroup());
                    components.add(getComponent(componentSpec.getName(), firstJob, showAggregatedPipeline));
                }
            }
            if (regexpFirstJobs != null) {
                for (RegExpSpec regexp : regexpFirstJobs) {
                    Map<String, AbstractProject> matches = ProjectUtil.getProjects(regexp.getRegexp());
                    for (Map.Entry<String, AbstractProject> entry : matches.entrySet()) {
                        components.add(getComponent(entry.getKey(), entry.getValue(), showAggregatedPipeline));
                    }
                }
            }
            if (getSorting() != null && !getSorting().equals(NONE_SORTER)) {
                ComponentComparatorDescriptor comparatorDescriptor = ComponentComparator.all().find(sorting);
                if (comparatorDescriptor != null) {
                    Collections.sort(components, comparatorDescriptor.createInstance());
                }
            }
            LOG.fine("Returning: " + components);
            error = null;
            return components;
        } catch (PipelineException e) {
            error = e.getMessage();
            return new ArrayList<Component>();
        }
    }

    private Component getComponent(String name, AbstractProject firstJob, boolean showAggregatedPipeline) throws PipelineException {
        Pipeline pipeline = Pipeline.extractPipeline(name, firstJob);
        List<Pipeline> pipelines = new ArrayList<Pipeline>();
        if (showAggregatedPipeline) {
            pipelines.add(pipeline.createPipelineAggregated(getOwnerItemGroup()));
        }
        pipelines.addAll(pipeline.createPipelineLatest(noOfPipelines, getOwnerItemGroup()));
        return new Component(name, firstJob.getName(), pipelines);
    }

    @Override
    public Collection<TopLevelItem> getItems() {
        List<TopLevelItem> result = new ArrayList<TopLevelItem>();
        Set<AbstractProject> projects = new HashSet<AbstractProject>();
        if (componentSpecs != null) {
            for (ComponentSpec componentSpec : componentSpecs) {
                projects.add(ProjectUtil.getProject(componentSpec.getFirstJob(), getOwnerItemGroup()));
            }
        }
        if (regexpFirstJobs != null) {
            for (RegExpSpec regexp : regexpFirstJobs) {
                Map<String, AbstractProject> projectMap = ProjectUtil.getProjects(regexp.getRegexp());
                projects.addAll(projectMap.values());
            }

        }
        for (AbstractProject project : projects) {
            Collection<AbstractProject<?, ?>> downstreamProjects = ProjectUtil.getAllDownstreamProjects(project).values();
            for (AbstractProject<?, ?> abstractProject : downstreamProjects) {
                result.add(getItem(abstractProject.getName()));
            }
        }

        return result;
    }

    @Override
    public boolean contains(TopLevelItem item) {
        return getItems().contains(item);
    }

    @Override
    protected void submit(StaplerRequest req) throws IOException, ServletException, Descriptor.FormException {
        req.bindJSON(this, req.getSubmittedForm());
        componentSpecs = req.bindJSONToList(ComponentSpec.class, req.getSubmittedForm().get("componentSpecs"));
        regexpFirstJobs = req.bindJSONToList(RegExpSpec.class, req.getSubmittedForm().get("regexpFirstJobs"));

    }

    @Override
    public Item doCreateItem(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        View allView;
        
        if (!isDefault()) {
            return getOwner().getPrimaryView().doCreateItem(req, rsp);
        }
        
        // when we are the default view, dynamically load the "All" view
        // and defer to the "All" view's implemenation for creating a new item
        try {
            allView = new hudson.model.AllView("All" , getOwner());
        } catch(Exception e) {
            // return null in the unlikely event that the AllView class
            // changes its constructor signatures, which would raise a NoSuchMethodException
            return null;
        }
        
        return allView.doCreateItem(req, rsp);
    }


    @Extension
    public static class DescriptorImpl extends ViewDescriptor {
        public ListBoxModel doFillNoOfColumnsItems(@AncestorInPath ItemGroup<?> context) {
            ListBoxModel options = new ListBoxModel();
            options.add("1", "1");
            options.add("2", "2");
            options.add("3", "3");
            return options;
        }

        public ListBoxModel doFillNoOfPipelinesItems(@AncestorInPath ItemGroup<?> context) {
            ListBoxModel options = new ListBoxModel();
            for (int i = 0; i <= MAX_NO_OF_PIPELINES; i++) {
                String opt = String.valueOf(i);
                options.add(opt, opt);
            }
            return options;
        }

        public ListBoxModel doFillSortingItems() {
            DescriptorExtensionList<ComponentComparator, ComponentComparatorDescriptor> descriptors = ComponentComparator.all();
            ListBoxModel options = new ListBoxModel();
            options.add("None", NONE_SORTER);
            for (ComponentComparatorDescriptor descriptor : descriptors) {
                options.add(descriptor.getDisplayName(), descriptor.getId());
            }
            return options;
        }

        public FormValidation doCheckUpdateInterval(@QueryParameter String value) {
            int valueAsInt;
            try {
                valueAsInt = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return FormValidation.error(e, "Value must be a integer");
            }
            if (valueAsInt <= 0) {
                return FormValidation.error("Value must be greater that 0");
            }
            return FormValidation.ok();
        }

        @Override
        public String getDisplayName() {
            return "Delivery Pipeline View";
        }
    }

    public static class RegExpSpec extends AbstractDescribableImpl<RegExpSpec> {

        private String regexp;

        @DataBoundConstructor
        public RegExpSpec(String regexp) {
            this.regexp = regexp;
        }

        public String getRegexp() {
            return regexp;
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<RegExpSpec> {

            @Override
            public String getDisplayName() {
                return "RegExp";
            }

            public FormValidation doCheckRegexp(@QueryParameter String value) {
                if (value != null) {
                    try {
                        Pattern pattern = Pattern.compile(value);
                        if (pattern.matcher("").groupCount() == 1) {
                            return FormValidation.ok();
                        } else {
                            return FormValidation.error("No capture group defined");
                        }
                    } catch (PatternSyntaxException e) {
                        return FormValidation.error(e, "Syntax error in regular-expression pattern");
                    }
                }
                return FormValidation.ok();
            }
        }
    }

    public static class ComponentSpec extends AbstractDescribableImpl<ComponentSpec> {
        private String name;
        private String firstJob;

        @DataBoundConstructor
        public ComponentSpec(String name, String firstJob) {
            this.name = name;
            this.firstJob = firstJob;
        }

        public String getName() {
            return name;
        }

        public String getFirstJob() {
            return firstJob;
        }

        public void setFirstJob(String firstJob) {
            this.firstJob = firstJob;
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<ComponentSpec> {

            @Override
            public String getDisplayName() {
                return "";
            }

            public ListBoxModel doFillFirstJobItems(@AncestorInPath ItemGroup<?> context) {
                return ProjectUtil.fillAllProjects(context);
            }

            public FormValidation doCheckName(@QueryParameter String value) {
                if (value != null && !"".equals(value.trim())) {
                    return FormValidation.ok();
                } else {
                    return FormValidation.error("Please supply a title!");
                }
            }

        }
    }
}
