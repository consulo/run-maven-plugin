package consulo.maven.packaging;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.jar.JarFile;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

/**
 * @author VISTALL
 * @since 24-Nov-17
 */
@Mojo(name = "patchPluginXml", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE)
public class PatchPluginXmlMojo extends AbstractPackagingMojo
{
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException
	{
		if(packaging.skip)
		{
			return;
		}

		patchPluginXml(this);
	}

	static void patchPluginXml(AbstractPackagingMojo mojo) throws MojoFailureException
	{
		String outputDirectory = mojo.myProject.getBuild().getOutputDirectory();

		File file = new File(outputDirectory);
		if(!file.exists())
		{
			return;
		}

		File pluginXmlFile = new File(file, "META-INF/plugin.xml");
		if(!pluginXmlFile.exists())
		{
			throw new MojoFailureException("'plugin.xml' file is not found");
		}

		String platformVersion = findConsuloVersion(mojo.myProject);

		mojo.getLog().info("patching xml: " + pluginXmlFile.getAbsolutePath());
		try
		{
			SAXBuilder builder = new SAXBuilder();
			builder.setXMLReaderFactory(XMLReaders.NONVALIDATING);

			Document document = builder.build(pluginXmlFile);

			Element rootElement = document.getRootElement();

			Element versionElement = rootElement.getChild("version");
			if(versionElement != null)
			{
				versionElement.setText(mojo.packaging.version);
			}
			else
			{
				rootElement.addContent(new Element("version").setText(mojo.packaging.version));
			}

			Element platformVersionElement = rootElement.getChild("platformVersion");
			if(platformVersionElement != null)
			{
				platformVersionElement.setText(platformVersion);
			}
			else
			{
				rootElement.addContent(new Element("platformVersion").setText(platformVersion));
			}

			XMLOutputter outputter = new XMLOutputter(Format.getRawFormat());
			try (FileWriter fileWriter = new FileWriter(pluginXmlFile))
			{
				outputter.output(document, fileWriter);
			}
		}
		catch(JDOMException | IOException e)
		{
			throw new MojoFailureException(e.getMessage(), e);
		}
	}

	public static String findConsuloVersion(MavenProject mavenProject) throws MojoFailureException
	{
		Map<String, Artifact> artifactMap = mavenProject.getArtifactMap();

		Artifact coreApiArtifact = artifactMap.get("consulo:consulo-core-api");
		if(coreApiArtifact == null)
		{
			throw new MojoFailureException("Artifact 'consulo:consulo-core-api' not resolved");
		}

		File coreApiJarFile = coreApiArtifact.getFile();

		String platformVersion;
		try (JarFile jarFile = new JarFile(coreApiJarFile))
		{
			String buildNumber = jarFile.getManifest().getMainAttributes().getValue("Consulo-Build-Number");
			if(buildNumber == null)
			{
				throw new MojoFailureException("Artifact 'consulo:consulo-core-api' not contains 'Consulo-Build-Number' attribute");
			}

			platformVersion = buildNumber;
		}
		catch(IOException e)
		{
			throw new MojoFailureException(e.getMessage(), e);
		}

		return platformVersion;
	}
}
