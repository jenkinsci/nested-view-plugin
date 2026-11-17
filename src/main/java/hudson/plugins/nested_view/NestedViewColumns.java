package hudson.plugins.nested_view;

import com.google.common.collect.Lists;
import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import hudson.model.Saveable;
import hudson.util.DescribableList;
import hudson.views.ListViewColumn;
import hudson.views.ListViewColumnDescriptor;
import hudson.views.StatusColumn;
import hudson.views.WeatherColumn;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest2;

import java.io.IOException;
import java.util.List;

/**
 * NestedView delegates things related to optional columns to this class.
 *
 * @author stanislav bashkirtsev
 */
public class NestedViewColumns {
    private DescribableList<ListViewColumn, Descriptor<ListViewColumn>> columns;

    /**
     * @deprecated Handled by {@link StaplerRequest2#bindJSON(Object, JSONObject)} in
     *   {@link NestedView#submit(StaplerRequest2)} now.
     */
    @Deprecated
    public void updateFromForm(StaplerRequest2 req, JSONObject formData, String key) throws IOException, Descriptor.FormException {
        columns.rebuildHetero(req, formData, getPossibleColumns(), key);
    }

    public void initializeColumns(Saveable owner) {
        if (columns == null) {
            columns = new DescribableList<>(owner);
        }
    }

    /**
     * @deprecated Use {@link #initializeColumns(Saveable)} instead.
     */
    @Deprecated
    public void setColumns(DescribableList<ListViewColumn, Descriptor<ListViewColumn>> columns) {
        this.columns = columns;
    }

    public DescribableList<ListViewColumn, Descriptor<ListViewColumn>> getColumns() {
        return columns;
    }

    public boolean isShowStatusColumn() {
        return containsColumnWithDescriptor(StatusColumn.DescriptorImpl.class);
    }

    public boolean isShowWeatherColumn() {
        return containsColumnWithDescriptor(WeatherColumn.DescriptorImpl.class);
    }

    public static List<Descriptor<ListViewColumn>> getPossibleColumns() {
        return extractOptionalColumns(ListViewColumn.all());
    }

    private boolean containsColumnWithDescriptor(Class<? extends ListViewColumnDescriptor> descriptorClass) {
        for (ListViewColumn column : columns) {
            if (column.getDescriptor().getClass() == descriptorClass) {
                return true;
            }
        }
        return false;
    }

    private static List<Descriptor<ListViewColumn>> extractOptionalColumns(
            DescriptorExtensionList<ListViewColumn, Descriptor<ListViewColumn>> extensionList) {
        return Lists.<Descriptor<ListViewColumn>>newArrayList(
                extensionList.get(WeatherColumn.DescriptorImpl.class),
                extensionList.get(StatusColumn.DescriptorImpl.class));
    }
}
