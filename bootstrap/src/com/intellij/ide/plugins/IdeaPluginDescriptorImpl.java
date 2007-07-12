package com.intellij.ide.plugins;

import com.intellij.CommonBundle;
import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * @author mike
 */
public class IdeaPluginDescriptorImpl implements IdeaPluginDescriptor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.plugins.PluginDescriptor");

  public static final IdeaPluginDescriptorImpl[] EMPTY_ARRAY = new IdeaPluginDescriptorImpl[0];
  private String myName;
  private PluginId myId;
  private String myDescription;
  private String myResourceBundleBaseName;
  private String myChangeNotes;
  private String myVersion;
  private String myVendor;
  private String myVendorEmail;
  private String myVendorUrl;
  private String myVendorLogoPath;
  private String myCategory;
  private String url;
  private File myPath;
  private PluginId[] myDependencies;
  private PluginId[] myOptionalDependencies;
  private Element myActionsElement = null;
  private ComponentConfig[] myAppComponents = null;
  private ComponentConfig[] myProjectComponents = null;
  private ComponentConfig[] myModuleComponents = null;
  private boolean myDeleted = false;
  private ClassLoader myLoader;
  private HelpSetPath[] myHelpSets;
  private List<Element> myExtensions;
  private List<Element> myExtensionsPoints;
  private String myDescriptionChildText;
  private String myDownloadCounter;
  private long myDate;
  private boolean myUseIdeaClassLoader;
  private boolean myEnabled = true;

  private String mySinceBuild;
  private String myUntilBuild;

  public IdeaPluginDescriptorImpl(File pluginPath) {
    myPath = pluginPath;
  }

  public void setPath(File path) {
    myPath = path;
  }

  public File getPath() {
    return myPath;
  }

  public void readExternal(final URL url) throws InvalidDataException, FileNotFoundException {
    try {
      readExternal(JDOMUtil.loadDocument(url).getRootElement());
    }
    catch (FileNotFoundException e) {
      throw e;
    }
    catch (IOException e) {
      throw new InvalidDataException(e);
    }
    catch (JDOMException e) {
      throw new InvalidDataException(e);
    }
  }

  private void readExternal(Element element) throws InvalidDataException {
    final PluginBean pluginBean = XmlSerializer.deserialize(element, PluginBean.class);

    url = pluginBean.url;
    myName = pluginBean.name;
    String idString = pluginBean.id;
    if (idString == null || idString.length() == 0) {
      idString = myName;
    }
    myId = PluginId.getId(idString);

    String internalVersionString = pluginBean.formatVersion;
    if (internalVersionString != null) {
      try {
        final int formatVersion = Integer.parseInt(internalVersionString);
      }
      catch (NumberFormatException e) {
        LOG.error(new PluginException("Invalid value in plugin.xml format version: " + internalVersionString, e, myId));
      }
    }
    myUseIdeaClassLoader = pluginBean.useIdeaClassLoader;
    if (pluginBean.ideaVersion != null) {
      mySinceBuild = pluginBean.ideaVersion.sinceBuild;
      myUntilBuild = pluginBean.ideaVersion.untilBuild;
    }

    myResourceBundleBaseName = pluginBean.resourceBundle;

    myDescriptionChildText = pluginBean.description;
    myChangeNotes = pluginBean.changeNotes;
    myVersion = pluginBean.pluginVersion;
    myCategory = pluginBean.category;


    if (pluginBean.vendor != null) {
      myVendor = pluginBean.vendor.name;
      myVendorEmail = pluginBean.vendor.email;
      myVendorUrl = pluginBean.vendor.url;
      myVendorLogoPath = pluginBean.vendor.logo;
    }

    Set<PluginId> dependentPlugins = new HashSet<PluginId>();
    Set<PluginId> optionalDependentPlugins = new HashSet<PluginId>();
    if (pluginBean.dependencies != null) {
      for (PluginDependency dependency : pluginBean.dependencies) {
        String text = dependency.pluginId;
        if (text != null && text.length() > 0) {
          final PluginId id = PluginId.getId(text);
          dependentPlugins.add(id);
          if (dependency.optional) {
            optionalDependentPlugins.add(id);
          }
        }
      }
    }
    myDependencies = dependentPlugins.toArray(new PluginId[dependentPlugins.size()]);
    myOptionalDependencies = optionalDependentPlugins.toArray(new PluginId[optionalDependentPlugins.size()]);


    List<HelpSetPath> hsPathes = new ArrayList<HelpSetPath>();
    if (pluginBean.helpSets != null) {
      for (PluginHelpSet pluginHelpSet : pluginBean.helpSets) {
        HelpSetPath hsPath = new HelpSetPath(pluginHelpSet.file, pluginHelpSet.path);
        hsPathes.add(hsPath);
      }
    }
    myHelpSets = hsPathes.toArray(new HelpSetPath[hsPathes.size()]);

    myAppComponents = pluginBean.applicationComponents;
    myProjectComponents = pluginBean.projectComponents;
    myModuleComponents = pluginBean.moduleComponents;

    if (myAppComponents == null) myAppComponents = ComponentConfig.EMPTY_ARRAY;
    if (myProjectComponents == null) myProjectComponents = ComponentConfig.EMPTY_ARRAY;
    if (myModuleComponents == null) myModuleComponents = ComponentConfig.EMPTY_ARRAY;

    if (pluginBean.extensions != null) {
      myExtensions = new ArrayList<Element>();
      for (Element extensionsRoot : pluginBean.extensions) {
        for (final Object o : extensionsRoot.getChildren()) {
          myExtensions.add((Element)o);
        }
      }
    }

    if (pluginBean.extensionPoints != null) {
      myExtensionsPoints = new ArrayList<Element>();
      for (Element root : pluginBean.extensionPoints) {
        for (Object o : root.getChildren()) {
          myExtensionsPoints.add((Element)o);
        }
      }
    }

    myActionsElement = pluginBean.actions;
  }

  private static String loadDescription(final String descriptionChildText, @Nullable final ResourceBundle bundle, final PluginId id) {
    if (bundle == null) {
      return descriptionChildText;
    }

    return CommonBundle.messageOrDefault(bundle, createDescriptionKey(id), descriptionChildText);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String createDescriptionKey(final PluginId id) {
    return "plugin." + id + ".description";
  }

  void registerExtensions() {
    if (myExtensions != null || myExtensionsPoints != null) {
      Extensions.getRootArea().getExtensionPoint(Extensions.AREA_LISTENER_EXTENSION_POINT).registerExtension(new AreaListener() {
        public void areaCreated(String areaClass, AreaInstance areaInstance) {
          final ExtensionsArea area = Extensions.getArea(areaInstance);
          area.registerAreaExtensionsAndPoints(IdeaPluginDescriptorImpl.this, myExtensionsPoints, myExtensions);
        }

        public void areaDisposing(String areaClass, AreaInstance areaInstance) {
        }
      });
    }
  }

  public String getDescription() {
    return myDescription;
  }

  public String getChangeNotes() {
    return myChangeNotes;
  }

  public String getName() {
    return myName;
  }

  @NotNull
  public PluginId[] getDependentPluginIds() {
    return myDependencies;
  }


  @NotNull
  public PluginId[] getOptionalDependentPluginIds() {
    return myOptionalDependencies;
  }

  public String getVendor() {
    return myVendor;
  }

  public String getVersion() {
    return myVersion;
  }

  public String getResourceBundleBaseName() {
    return myResourceBundleBaseName;
  }

  public String getCategory() {
    return myCategory;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public List<File> getClassPath() {
    if (myPath.isDirectory()) {
      final List<File> result = new ArrayList<File>();
      final File classesDir = new File(myPath, "classes");

      if (classesDir.exists()) {
        result.add(classesDir);
      }

      final File[] files = new File(myPath, "lib").listFiles();
      if (files != null && files.length > 0) {
        for (final File f : files) {
          if (f.isFile()) {
            final String name = f.getName();
            if (StringUtil.endsWithIgnoreCase(name, ".jar") || StringUtil.endsWithIgnoreCase(name, ".zip")) {
              result.add(f);
            }
          }
          else {
            result.add(f);
          }
        }
      }

      return result;
    }
    else {
      return Collections.singletonList(myPath);
    }
  }

  public Element getActionsDescriptionElement() {
    return myActionsElement;
  }

  @NotNull
  public ComponentConfig[] getAppComponents() {
    return myAppComponents;
  }

  @NotNull
  public ComponentConfig[] getProjectComponents() {
    return myProjectComponents;
  }

  @NotNull
  public ComponentConfig[] getModuleComponents() {
    return myModuleComponents;
  }

  public String getVendorEmail() {
    return myVendorEmail;
  }

  public String getVendorUrl() {
    return myVendorUrl;
  }

  public String getUrl() {
    return url;
  }

  @NonNls
  public String toString() {
    return "PluginDescriptor[name='" + myName + "', classpath='" + myPath + "']";
  }

  public boolean isDeleted() {
    return myDeleted;
  }

  public void setDeleted(boolean deleted) {
    myDeleted = deleted;
  }

  public void setLoader(ClassLoader loader) {
    myLoader = loader;

    //Now we're ready to load root area extensions

    Extensions.getRootArea().registerAreaExtensionsAndPoints(this, myExtensionsPoints, myExtensions);

    initialize(getPluginClassLoader());
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof IdeaPluginDescriptorImpl)) return false;

    final IdeaPluginDescriptorImpl pluginDescriptor = (IdeaPluginDescriptorImpl)o;

    if (myName != null ? !myName.equals(pluginDescriptor.myName) : pluginDescriptor.myName != null) return false;

    return true;
  }

  public int hashCode() {
    return (myName != null ? myName.hashCode() : 0);
  }

  @NotNull
  public HelpSetPath[] getHelpSets() {
    return myHelpSets;
  }

  public PluginId getPluginId() {
    return myId;
  }

  /*
     This setter was explicitly defined to be able to set a category for a
     descriptor outside its loading from the xml file.
     Problem was that most commonly plugin authors do not publish the plugin's
     category in its .xml file so to be consistent in plugins representation
     (e.g. in the Plugins form) we have to set this value outside.
  */
  public void setCategory( String category ){
    myCategory = category;
  }

  /*
     This setter was explicitly defined to be able to set downloads count for a
     descriptor outside its loading from the xml file since this information
     is available only from the site.
  */
  public void setDownloadsCount( String dwnlds ){
    myDownloadCounter = dwnlds;
  }

  public String getDownloads(){
    return myDownloadCounter;
  }

  /*
     This setter was explicitly defined to be able to set date for a
     descriptor outside its loading from the xml file since this information
     is available only from the site.
  */
  public void setDate( long date ){
    myDate = date;
  }

  public long getDate(){
    return myDate;
  }

  public void setVendor( final String val )
  {
    myVendor = val;
  }
  public void setVendorEmail( final String val )
  {
    myVendorEmail = val;
  }
  public void setVendorUrl( final String val )
  {
    myVendorUrl = val;
  }
  public void setUrl( final String val )
  {
    url = val;
  }

  public ClassLoader getPluginClassLoader() {
    return myLoader != null ? myLoader : getClass().getClassLoader();
  }

  public String getVendorLogoPath() {
    return myVendorLogoPath;
  }

  public boolean getUseIdeaClassLoader() {
    return myUseIdeaClassLoader;
  }

  public void setVendorLogoPath(final String vendorLogoPath) {
    myVendorLogoPath = vendorLogoPath;
  }

  public void initialize(@NotNull ClassLoader classLoader) {
    ResourceBundle bundle = null;
    if (myResourceBundleBaseName != null) {
      try {
        bundle = ResourceBundle.getBundle(myResourceBundleBaseName, Locale.getDefault(), classLoader);
      }
      catch (MissingResourceException e) {
        LOG.info("Cannot find plugin " + myId + " resource-bundle: " + myResourceBundleBaseName);
      }
    }

    myDescription = loadDescription(myDescriptionChildText, bundle, myId);
  }

  public void insertDependency(final IdeaPluginDescriptor d) {
    PluginId[] deps = new PluginId[getDependentPluginIds().length + 1];
    deps[0] = d.getPluginId();
    System.arraycopy(myDependencies, 0, deps, 1, deps.length - 1);
    myDependencies = deps;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  public void setEnabled(final boolean enabled) {
    myEnabled = enabled;
  }

  public String getSinceBuild() {
    return mySinceBuild;
  }

  public String getUntilBuild() {
    return myUntilBuild;
  }
}
