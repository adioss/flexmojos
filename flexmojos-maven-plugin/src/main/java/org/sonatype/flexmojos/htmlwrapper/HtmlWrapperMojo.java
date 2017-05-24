/**
 * Flexmojos is a set of maven goals to allow maven users to compile, optimize and test Flex SWF, Flex SWC, Air SWF and Air SWC.
 * Copyright (C) 2008-2012  Marvin Froeder <marvin@flexmojos.net>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.sonatype.flexmojos.htmlwrapper;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.util.URIUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.sonatype.flexmojos.MavenMojo;
import org.sonatype.flexmojos.utilities.FileInterpolationUtil;
import org.sonatype.flexmojos.utilities.MavenUtils;

import eu.cedarsoft.utils.ZipExtractor;

/**
 * This goal generate the html wrapper to Flex applications, like what is done by flex builder.
 *
 * @author Marvin Herman Froeder (velo.br@gmail.com)
 * @since 1.0
 * @phase generate-resources
 * @goal wrapper
 * @author marvin
 */
public class HtmlWrapperMojo
    extends AbstractMojo
    implements MavenMojo
{

    private static final String INDEX_TEMPLATE_HTML = "index.template.html";

    /**
     * @component
     */
    protected ArtifactFactory artifactFactory;

    /**
     * @parameter expression="${project.build}"
     * @required
     * @readonly
     */
    protected Build build;

    /**
     * LW : needed for expression evaluation The maven MojoExecution needed for ExpressionEvaluation
     *
     * @parameter expression="${session}"
     * @required
     * @readonly
     */
    protected MavenSession context;

    /**
     * final name of html file<br/>
     * <br/>
     * This is now deprecated, and only supplied for backwards compatibility.
     *
     * @parameter default-value="${project.build.finalName}"
     * @deprecated
     */
    private String htmlName;

    /**
     * @parameter expression="${localRepository}"
     * @required
     * @readonly
     */
    private ArtifactRepository localRepository;

    /**
     * @component
     */
    private MavenProjectBuilder mavenProjectBuilder;

    /**
     * output Directory to store final html <br/>
     * <br/>
     * This is ignored if running in project with war packaging.
     *
     * @parameter default-value="${project.build.directory}"
     */
    private File outputDirectory;

    /**
     * Used to define parameters that will be replaced. Usage:
     *
     * <pre>
     *  &lt;parameters&gt;
     *      &lt;swf&gt;${build.finalName}&lt;/swf&gt;
     *      &lt;width&gt;100%&lt;/width&gt;
     *      &lt;height&gt;100%&lt;/height&gt;
     *  &lt;/parameters&gt;
     * </pre>
     *
     * The following prameters wil be injected if not defined:
     * <ul>
     * title
     * </ul>
     * <ul>
     * version_major
     * </ul>
     * <ul>
     * version_minor
     * </ul>
     * <ul>
     * version_revision
     * </ul>
     * <ul>
     * swf
     * </ul>
     * <ul>
     * width
     * </ul>
     * <ul>
     * height
     * </ul>
     * <ul>
     * bgcolor
     * </ul>
     * <ul>
     * application
     * </ul>
     * If you are using a custom template, and wanna some extra parameters, this is the right place to define it. <br/>
     * <br/>
     * This is ignored if running in project with war packaging.
     *
     * @parameter
     */
    private Map<String, Object> parameters;

    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * @parameter expression="${project.remoteArtifactRepositories}"
     * @required
     * @readonly
     */
    private List<ArtifactRepository> remoteRepositories;

    /**
     * @component
     */
    private ArtifactResolver resolver;

    /**
     * An external pom that provides wrapper parameters in place of the current one.
     */
    private MavenProject sourceProject;

    /**
     * specifies the version of the player the application is targeting. Features requiring a later version will not be
     * compiled into the application. The minimum value supported is "9.0.0". If not defined will take the default value
     * from current playerglobal dependency.
     *
     * @parameter
     */
    private String targetPlayer;

    /**
     * Files to not interpolate while copying files. Usually binary files. Accepts wild cards. By default, many common
     * binary formats are excluded (see useDefaultBinaryExcludes). Usage:
     *
     * <pre>
     *  &lt;templateExclusions&gt;
     *      &lt;String&gt;**&#047;*.xml&lt;/String&gt;
     *      &lt;String&gt;some-directory/&lt;/String&gt;
     *      &lt;String&gt;another-directory/**&#047;*.jsp&lt;/String&gt;
     *  &lt;/templateExclusions&gt;
     * </pre>
     *
     * In the above, the following applies in order:
     * <ol>
     * <li>Exclude all xml files</li>
     * <li>Exclude everything in the directory 'some-directory'</li>
     * <li>Exclude all jsp files in the directory 'another-directory'</li>
     * </ol>
     *
     * @parameter
     */
    private String[] templateExclusions;

    /**
     * Files to interpolate while copying files. Accepts wild cards. By default includes all files. Any patterns defined
     * in templateExclusions, the default binaries excludes, or default plexus excludes (svn, cvs, temp files, etc) will
     * be applied on top of this, so matching a pattern here does not force that file to be wrapped. Usage:
     *
     * <pre>
     *  &lt;templateExclusions&gt;
     *      &lt;String&gt;**&#047;*.xml&lt;/String&gt;
     *      &lt;String&gt;some-directory/&lt;/String&gt;
     *      &lt;String&gt;another-directory/**&#047;*.jsp&lt;/String&gt;
     *  &lt;/templateExclusions&gt;
     * </pre>
     *
     * In the above, the following applies in order:
     * <ol>
     * <li>Include all xml files</li>
     * <li>Include everything in the directory 'some-directory'</li>
     * <li>Include all jsp files in the directory 'another-directory'</li>
     * </ol>
     *
     * @parameter
     */
    private String[] templateInclusions;

    /**
     * output Directory to store final html <br/>
     * <br/>
     * This is ignored if running in project with war packaging.
     *
     * @parameter default-value="${project.build.directory}/html-wrapper-template"
     */
    private File templateOutputDirectory;

    /**
     * The template URI.
     * <p>
     * You can point to a zip file, a folder or use one of the following embed templates:
     * <ul>
     * embed:client-side-detection
     * </ul>
     * <ul>
     * embed:client-side-detection-with-history
     * </ul>
     * <ul>
     * embed:express-installation
     * </ul>
     * <ul>
     * embed:express-installation-with-history
     * </ul>
     * <ul>
     * embed:no-player-detection
     * </ul>
     * <ul>
     * embed:no-player-detection-with-history
     * </ul>
     * To point to a zip file you must use a URI like this:
     *
     * <pre>
     * zip:/myTemplateFolder/template.zip
     * zip:c:/myTemplateFolder/template.zip
     * </pre>
     *
     * To point to a folder use a URI like this:
     *
     * <pre>
     * folder:/myTemplateFolder/
     * folder:c:/myTemplateFolder/
     * </pre>
     * <p>
     * This mojo will look for <tt>index.template.html</tt> for replace parameters. <br/>
     * <br/>
     * This is ignored if running in project with war packaging.
     *
     * @parameter default-value="embed:express-installation-with-history"
     */
    private String templateURI;

    /**
     * Controls whether or not common binary file types are excluded by default when choosing what files to wrap. Useful
     * to set to false if for some reason you decide to name a wrapped file something like "index.exe" or
     * "html-wrapper.png" for some unanticipated reason.
     *
     * @parameter default-value="true"
     */
    private boolean useDefaultBinaryExcludes;

    /**
     * In the context of a war project, this specifies the external artifact that the wrapper parameters will be
     * extracted from. Usage:
     *
     * <pre>
     *  &lt;wrapperArtifact&gt;
     *      &lt;groupId&gt;com.company&lt;/groupId&gt;
     *      &lt;artifactId&gt;some-project&lt;/artifactId&gt;
     *      &lt;version&gt;3.2.7%&lt;/version&gt;
     *      &lt;classifier&gt;prod%&lt;/classifier&gt;
     *  &lt;/wrapperArtifact&gt;
     * </pre>
     *
     * Both groupId and artifactId are required, but version and classifier are optional and can be inferred from a
     * dependency if present (for example when the copy-flex-resources goal is executed).
     *
     * @parameter
     */
    private Map<String, String> wrapperArtifact;

    private Artifact convertToArtifact( Dependency dependency )
    {
        return artifactFactory.createArtifactWithClassifier( dependency.getGroupId(), dependency.getArtifactId(),
                                                             dependency.getVersion(), dependency.getType(),
                                                             dependency.getClassifier() );
    }

    private void copyEmbedTemplate( String path )
        throws MojoExecutionException
    {
        URL url = getClass().getResource( "/templates/wrapper/" + path + ".zip" );
        File template = new File( templateOutputDirectory, "template.zip" );
        try
        {
            FileUtils.copyURLToFile( url, template );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Unable to copy template to: " + template, e );
        }
        extractZipTemplate( templateOutputDirectory, template );
    }

    private void copyFolderTemplate( String path )
        throws MojoExecutionException
    {
        File source = new File( path );
        if ( !source.isAbsolute() )
        {
            source = new File( project.getBasedir(), path );
        }
        if ( !source.exists() || !source.isDirectory() )
        {
            throw new MojoExecutionException( "Template folder doesn't exists. " + source );
        }

        try
        {
            FileUtils.copyDirectory( source, templateOutputDirectory,
                                     FileFilterUtils.makeSVNAware( FileFilterUtils.makeCVSAware( null ) ) );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Unable to copy template to: " + templateOutputDirectory, e );
        }
    }

    private void copyIndexTemplate()
        throws MojoExecutionException
    {
        File indexTemplate = new File( templateOutputDirectory, INDEX_TEMPLATE_HTML );
        if ( !indexTemplate.isFile() )
        {
            getLog().debug( "No index.template.html" );
            return;
        }
        File index = new File( outputDirectory, htmlName + ".html" );

        try
        {
            FileInterpolationUtil.copyFile( indexTemplate, index, parameters );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Unable to write " + index, e );
        }
    }

    private void copySurroundingFiles()
        throws MojoExecutionException
    {
        try
        {
            FileInterpolationUtil.copyDirectory( templateOutputDirectory, outputDirectory, parameters,
                                                 templateExclusions, templateInclusions, useDefaultBinaryExcludes );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Unable to create templates.", e );
        }

        // XXX shouldn't copy template, but there isn't a fast fix for that right know
        File template = new File( outputDirectory, INDEX_TEMPLATE_HTML );
        if ( template.exists() )
        {
            template.delete();
        }
    }

    private void copyZipTemplate( String path )
        throws MojoExecutionException
    {
        File source = new File( path );
        if ( !source.exists() || !source.isFile() )
        {
            throw new MojoExecutionException( "Zip template doesn't exists. " + source );
        }

        extractZipTemplate( templateOutputDirectory, source );
    }

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        String packaging = project.getPackaging();

        if ( !"swf".equals( packaging ) )
        {
            loadExternalParams();

            if ( "war".equals( packaging ) )
            {
                rewireForWar();
            }
        }

        executeInternal();
    }

    private void executeInternal()
        throws MojoExecutionException, MojoFailureException
    {
        init();

        extractTemplate();
        copySurroundingFiles();
        copyIndexTemplate();
    }

    private void extractTemplate()
        throws MojoExecutionException
    {
        getLog().info( "Extracting template" );
        templateOutputDirectory.mkdirs();

        URI uri;
        try
        {
            if ( MavenUtils.isWindows() )
            {
                // Shake bars to avoid URI syntax problems
                templateURI = templateURI.replace( '\\', '/' );
            }
            templateURI = URIUtil.encodePath( templateURI );
            uri = new URI( templateURI );
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Invalid template URI.", e );
        }

        String scheme = uri.getScheme();
        if ( "embed".equals( scheme ) )
        {
            copyEmbedTemplate( uri.getSchemeSpecificPart() );
        }
        else if ( "zip".equals( scheme ) )
        {
            copyZipTemplate( uri.getSchemeSpecificPart() );
        }
        else if ( "folder".equals( scheme ) )
        {
            copyFolderTemplate( uri.getSchemeSpecificPart() );
        }
        else
        {
            throw new MojoExecutionException( "Invalid URI scheme: " + scheme );
        }

    }

    private void extractZipTemplate( File outputDir, File template )
        throws MojoExecutionException
    {
        try
        {
            ZipExtractor ze = new ZipExtractor( template );
            ze.extract( outputDir );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "An error happens when trying to extract html-template.", e );
        }
    }

    /*
     * Copied from CopyMojo... move to org.sonatype.flexmojos.utilities.MavenUtils?
     */
    @SuppressWarnings( "unchecked" )
    private Artifact findArtifact( MavenProject project, String groupId, String artifactId, String version,
                                   String type, String classifier )
        throws MojoExecutionException
    {
        // Dependencies must be traversed instead of artifacts here because of the execution phase of the mojo
        List<Dependency> dependencies = project.getDependencies();

        for ( Dependency dependency : dependencies )
        {
            String matchGroupId = dependency.getGroupId();
            String matchArtifactId = dependency.getArtifactId();
            String matchType = dependency.getType();
            if ( groupId.equals( matchGroupId ) && artifactId.equals( matchArtifactId ) && type.equals( matchType ) )
            {
                if ( version != null )
                {
                    String matchVersion = dependency.getVersion();
                    if ( version.equals( matchVersion ) )
                    {
                        if ( classifier != null )
                        {
                            String matchClassifier = dependency.getClassifier();
                            if ( classifier.equals( matchClassifier ) )
                            {
                                return convertToArtifact( dependency );
                            }
                            else
                            {
                                getLog().warn(
                                               "Wrapper found matching artifact with classifier [" + matchClassifier
                                                   + "], but did not match requested classifier [" + classifier
                                                   + "] so it is being ignored" );
                            }
                        }
                        else
                        {
                            return convertToArtifact( dependency );
                        }
                    }
                    else
                    {
                        getLog().warn(
                                       "Wrapper found matching artifact with version [" + matchVersion
                                           + "], but did not match requested version [" + version
                                           + "] so it is being ignored" );
                    }
                }
                else
                {
                    return convertToArtifact( dependency );
                }
            }
        }

        return null;
    }

    public MavenSession getSession()
    {
        return context;
    }

    private void init()
    {
        /*
         * If sourceProject is defined, then parameters are from an external project and that project (sourceProject)
         * should be used as reference for default values rather than this project.
         */
        MavenProject project = this.project;
        if ( sourceProject != null )
        {
            project = sourceProject;
        }

        if ( parameters == null )
        {
            parameters = new HashMap<String, Object>();
        }

        if ( !parameters.containsKey( "title" ) )
        {
            parameters.put( "title", project.getName() );
        }

        String[] nodes = targetPlayer != null ? targetPlayer.split( "\\." ) : new String[] { "9", "0", "0" };
        if ( !parameters.containsKey( "version_major" ) )
        {
            parameters.put( "version_major", nodes[0] );
        }
        if ( !parameters.containsKey( "version_minor" ) )
        {
            parameters.put( "version_minor", nodes[1] );
        }
        if ( !parameters.containsKey( "version_revision" ) )
        {
            parameters.put( "version_revision", nodes[2] );
        }
        if ( !parameters.containsKey( "swf" ) )
        {
            parameters.put( "swf", project.getBuild().getFinalName() );
        }
        if ( !parameters.containsKey( "width" ) )
        {
            parameters.put( "width", "100%" );
        }
        if ( !parameters.containsKey( "height" ) )
        {
            parameters.put( "height", "100%" );
        }
        if ( !parameters.containsKey( "application" ) )
        {
            parameters.put( "application", project.getArtifactId() );
        }
        if ( !parameters.containsKey( "bgcolor" ) )
        {
            parameters.put( "bgcolor", "#869ca7" );
        }
    }

    /**
     * Loads the parameters value (from plugin configuration) from an externally referenced dependency pom rather than
     * the pom for the current project.
     *
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    @SuppressWarnings( "unchecked" )
    private void loadExternalParams()
        throws MojoExecutionException
    {
        Artifact sourceArtifact;
        if ( wrapperArtifact != null )
        {
            String groupId = wrapperArtifact.get( "groupId" );
            String artifactId = wrapperArtifact.get( "artifactId" );
            String version = wrapperArtifact.get( "version" );
            String classifier = wrapperArtifact.get( "classifier" );

            if ( groupId == null || artifactId == null )
            {
                throw new MojoExecutionException(
                                                  "Both groupId and artifactId are required within the wrapperArtifact configuration " );
            }

            // Version is optional at this point
            Artifact swfArtifact = findArtifact( project, groupId, artifactId, version, "swf", classifier );
            if ( swfArtifact != null )
            {
                // Found matching dependency, so use this as the basis for the target external pom artifact
                sourceArtifact =
                    artifactFactory.createArtifactWithClassifier( groupId, artifactId, swfArtifact.getVersion(), "swf",
                                                                  swfArtifact.getClassifier() );
            }
            else
            {
                // Could not find a matching dependency, so try to build from scratch
                if ( version == null )
                {
                    throw new MojoExecutionException(
                                                      "Can't find a matching swf dependency, and no version was provided.  "
                                                          + "Therefore, no external artifact can be located to wrap" );
                }

                sourceArtifact =
                    artifactFactory.createArtifactWithClassifier( groupId, artifactId, version, "swf", classifier );
            }
        }
        else
        {
            throw new MojoExecutionException(
                                              "The wrapperArtifact configuartion is required when wrapping an external swf " );
        }

        getLog().info( "Wrapping with external artifact:  " + sourceArtifact.toString() );
        MavenUtils.resolveArtifact( project, sourceArtifact, resolver, localRepository, remoteRepositories );
        this.sourceProject = loadProject( sourceArtifact );

        // Does source pom contain flexmojos plugin?
        Map<String, Plugin> sourcePlugins = sourceProject.getBuild().getPluginsAsMap();
        Plugin sourceFlexmojos = sourcePlugins.get( "org.sonatype.flexmojos:flexmojos-maven-plugin" );
        if ( sourceFlexmojos == null )
        {
            throw new MojoExecutionException( "Could not locate flexmojos plugin in wrapper source pom" );
        }

        this.parameters = MavenPluginUtil.extractParameters( sourceFlexmojos );
    }

    /**
     * Tries to construct project for the provided artifact
     *
     * @param artifact
     * @return MavenProject for the given artifact
     * @throws MojoExecutionException
     */
    private MavenProject loadProject( Artifact artifact )
        throws MojoExecutionException
    {
        try
        {
            return mavenProjectBuilder.buildFromRepository( artifact, remoteRepositories, localRepository );
        }
        catch ( ProjectBuildingException ex )
        {
            throw new MojoExecutionException( "Problems building project for:  " + artifact.getId(), ex );
        }
    }

    /**
     * Insert flexmojos wrapper process into maven-war-plugin's process by re-routing its warSourceDirectory
     * configuration to this.outputDirectory and using its original warSourceDirectory as the value for this.templateURI
     *
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    @SuppressWarnings( "unchecked" )
    private void rewireForWar()
        throws MojoExecutionException
    {
        // Fetch war plugin configuration
        Map<String, Plugin> plugins = build.getPluginsAsMap();
        Plugin warPlugin = plugins.get( "org.apache.maven.plugins:maven-war-plugin" );
        if ( warPlugin == null )
        {
            throw new MojoExecutionException( "Flexmojos HtmlWrapperMojo could not find the war plugin" );
        }
        Xpp3DomMap config = MavenPluginUtil.getParameters( warPlugin );

        // Map this.templateURI to folder:{warPlugin.warSourceDirectory)
        String warSourceDirectory = config.get( "warSourceDirectory" );
        if ( warSourceDirectory == null )
        {
            warSourceDirectory = project.getBasedir() + "/src/main/webapp";
        }
        this.templateURI = "folder:" + warSourceDirectory;

        // Map outputDirectory/templateOutputDirectory to warPlugin.workDirectory
        // so that they don't get packaged in war accidentally
        String workDirectory = config.get( "workDirectory" );
        if ( workDirectory == null )
        {
            workDirectory = build.getDirectory() + "/war/work";
        }
        this.templateOutputDirectory = new File( workDirectory, "extracted-template" );
        this.outputDirectory = new File( workDirectory, "wrapped-template" );

        // Map warPlugin.warSourceDirectory to this.outputDirectory
        config.put( "warSourceDirectory", outputDirectory.getAbsolutePath() );
    }

}
