/*
 * The Open SLEE project.
 *
 * The source code contained in this file is in in the public domain.
 * It can be used in any project or product without prior permission,
 * license or royalty payments. There is no claim of correctness and
 * NO WARRANTY OF ANY KIND provided with this code.
 */
package org.mobicents.slee.container.component.deployment;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;

import javax.slee.SLEEException;
import javax.slee.management.AlreadyDeployedException;
import javax.slee.management.DeployableUnitID;
import javax.slee.management.DeploymentException;

import org.jboss.logging.Logger;
import org.mobicents.slee.container.SleeContainer;
import org.mobicents.slee.container.component.DeployableUnitIDImpl;

/**
 * The DeploymentManager class is where all deployment starts. Using it comes
 * down to calling the deployUnit method by handing it an url pointing to the
 * location of a deployable unit jar. The jar would then be copied to a the
 * location pointed by the value of the system property:
 * 
 * org.mobicents.slee.container.management.deployment.DEPLOYMENT_DIRECTORY
 * 
 * and would be installed in the SLEE.
 * 
 * @author Emil Ivov
 */

public class DeploymentManager {
    private static Logger logger;

    private static byte buffer[];

    static {
        logger = Logger.getLogger(DeploymentManager.class);
        buffer = new byte[8192];
    }

    /**
     * Creates a new instance of the DeploymentManager.
     */
    public DeploymentManager() {
    }

    /**
     * Retrieves the deployable unit pointed to by the unitUrlStr.
     * 
     * @param sourceUrl
     *            the location of the deployable unit.
     * @param deploymentRootDir
     *            the location where the unit should be unjarred.
     * @param the
     *            container where new components should be installed.
     * @param classpathDirectory
     *            target directory for component classes
     * @throws NullPointerException
     *             if the specified url string is null
     * @throws DeploymentException
     *             if deployment fails for any reason
     * @return DeployableUnitID the newly created id corresponding to the
     *         deployable unit.
     */
    public DeployableUnitID deployUnit(URL sourceUrl, File deploymentRootDir,
            File classpathDirectory, SleeContainer componentContainer)
            throws NullPointerException, DeploymentException , AlreadyDeployedException {

        DeployableUnitIDImpl deployableUnitID;
        if (sourceUrl == null || deploymentRootDir == null || 
                classpathDirectory == null || componentContainer == null) {
            throw new NullPointerException("null arg!");
        }

        //make sure we unpack stuff in directories with unique names so that we
        //don't overwrite when we extract the jars (sbb.jar profile.jar ...)
        //from the deployable unit.
        
        File tempDUJarsDeploymentDir = createTempDUJarsDeploymentDir(deploymentRootDir, sourceUrl);

        //Get the DU jar file and put it in the local DU directory.
        File unitJarFile;
        try {
            unitJarFile = new File(tempDUJarsDeploymentDir, (new File(sourceUrl.getFile()))
                    .getName());
            InputStream is = sourceUrl.openStream();
            OutputStream os = new FileOutputStream(unitJarFile);
            //copy the file locally
            pipeStream(is, os);           
        } catch (IOException ioe) {
            throw new DeploymentException("Error retrieving file from URL=["
                    + sourceUrl + "] to local storage", ioe);
        }
        
        if(logger.isDebugEnabled()) {
        	logger.debug("Deploying from " + unitJarFile.getAbsolutePath());
        }

        //extract and deploy all jars found in the deployable unit.
        try {
 
            deployableUnitID = DeployableUnitDeployer
                    .deploy(sourceUrl, unitJarFile, tempDUJarsDeploymentDir, componentContainer);

         if(logger.isDebugEnabled()) {
            logger.debug("Installation of deployable unit successful");
         }

            return deployableUnitID;
        } catch (AlreadyDeployedException ex) {
            // clean up the mess here.
            unitJarFile.delete();
            throw ex;
        } catch (Exception ex) {
            // clean up the mess here.
            unitJarFile.delete();
            throw new DeploymentException("Could not deploy: " + ex.getMessage(), ex);
        } 
    }

    //============================= STATIC UTILITIES
    // ===============================

    /**
     * Pipes data from the input stream into the output stream.
     * 
     * @param is
     *            The InputStream where the data is coming from.
     * @param os
     *            The OutputStream where the data is going to.
     * @throws IOException
     *             if reading or writing the data fails.
     */
    static void pipeStream(InputStream is, OutputStream os) throws IOException {

        synchronized (buffer) {
            try {
                for (int bytesRead = is.read(buffer); bytesRead != -1; bytesRead = is
                        .read(buffer))
                    os.write(buffer, 0, bytesRead);

                is.close();
                os.close();
            } catch (IOException ioe) {
                try {
                    is.close();
                } catch (Exception ioexc) {/* do sth? */
                }
                try {
                    os.close();
                } catch (Exception ioexc) {/* do sth? */
                }
                throw ioe;
            }
        }
    }

    /**
     * Extracts the file with name <code>fileName</code> out of the
     * <code>containingJar</code> archive and stores it in <code>dstDir</code>.
     * 
     * @param fileName
     *            the name of the file to extract.
     * @param containingJar
     *            the archive where to extract it from.
     * @param dstDir
     *            the location where the extracted file should be stored.
     * @throws IOException
     *             if reading the archive or storing the extracted file fails
     * @return a <code>java.io.File</code> reference to the extracted file.
     */
    static File extractFile(String fileName, JarFile containingJar, File dstDir)
            throws IOException {
   
        ZipEntry zipFileEntry = containingJar.getEntry(fileName);
        logger.debug("Extracting file " + fileName + " from " + containingJar.getName());
        if (zipFileEntry == null) {
            logger.debug("Could not extract jar file entry " + fileName + " from " + containingJar.getName());
            return null;
        }
        
        File extractedFile = new File(dstDir, new File(zipFileEntry.getName()).getName());
        
        pipeStream(containingJar.getInputStream(zipFileEntry),
                new FileOutputStream(extractedFile));
        
        logger.debug("Extracted file " + extractedFile.getName() );
        
        return extractedFile;
    }
    
    /**
     * This method will extract all the files in the jar file
     * @param jarFile the jar file
     * @param dstDir the destination where files in the jar file be extracted
     * @param deployableUnitID 
     * @return 
     * @throws IOException failed to extract files
     */
    public static Set<String> extractJar(JarFile jarFile, File dstDir) throws IOException
	{
	  
    	Set<String> filesExtracted = new HashSet<String>();
	    //Extract jar contents to a classpath location
        JarInputStream jarIs = new JarInputStream(new BufferedInputStream(new FileInputStream( jarFile.getName())));
	    
	    for ( JarEntry entry = jarIs.getNextJarEntry();  jarIs.available()>0 && entry != null; entry = jarIs.getNextJarEntry())
	    {
	       
	        logger.debug("jar entry = " + entry.getName());
	       
	        //if(entry.getName().indexOf("META-INF") != -1){
	        //	logger.info("[###] UnPacking META-INF");
	            //continue;
	        //}

	        if( entry.isDirectory() )
	        {
	            //Create jar directories.
	            File dir = new File(dstDir, entry.getName());
	            if (!dir.exists() ) 
	            {
	                if ( !dir.mkdirs()) {
	                    logger.debug("Failed to create directory " + dir.getAbsolutePath());
	                    throw new IOException("Failed to create directory " +
	                                              dir.getAbsolutePath());
	                }
	            }
	            else
	                logger.debug("Created directory" + dir.getAbsolutePath());
	        }
	        else // unzip files
	        {
	        	File file = new File(dstDir, entry.getName());
	        	File dir = file.getParentFile();

	            if (!dir.exists() ) 
	            {
	                if ( !dir.mkdirs()) {
	                    logger.debug("Failed to create directory " + dir.getAbsolutePath());
	                    throw new IOException("Failed to create directory " +
	                                              dir.getAbsolutePath());
	                } else
	                	logger.debug("Created directory" + dir.getAbsolutePath());
	            }

			   
	            DeploymentManager.pipeStream(jarFile.getInputStream(entry),
	                                         new FileOutputStream(file));
	            filesExtracted.add(entry.getName());
	      
	            
	        }
	    }
	    jarIs.close();
	    jarFile.close();
	    return filesExtracted;
	}
    

    /**
     * Remove only stuff associated with a deployable unit id.
     * 
     * @param deployableUnitID --
     *            the ID of the deployable unit to undeplow.
     */

    public void undeployUnit(DeployableUnitIDImpl deployableUnitID) {

        // Delete the jar file itself.
        URI deploymentURI = deployableUnitID.getSourceURI();
        if (deploymentURI != null) {
            File srcFile = new File(deploymentURI);
            srcFile.delete();
            if(logger.isDebugEnabled()) {
            	logger.debug("Deleted DU jar file " + srcFile.getAbsolutePath());
            }
        }
        
        deployableUnitID.getDUDeployer().undeploy();
        
    }

    /**
     * 
     * Sets the directory that will be used for unpacking the child jars for a given DU. 
     * @TODO: make sure to remove the temp directory on undeploy
     * 
     * @param jarName The name of the jarFile which will be used as component in the temp deployment dir name
     * @throws IOException if the temp dir cannot be created
     */
    private File createTempDUJarsDeploymentDir(File rootDir, URL sourceUrl) {
        String jarName = new File(sourceUrl.getFile()).getName();
        try {
            // first create a dummy file to gurantee uniqueness. I would have been nice if the File class had a createTempDir() method
            // IVELIN -- do not use jarName here because windows cannot see the path (exceeds system limit)
            File tempDeploymentFile = File.createTempFile("tmpDUJars", "", rootDir);
                       
            File tempDUJarsDeploymentDir = new File(tempDeploymentFile.getAbsolutePath() + "-contents");
            if (!tempDUJarsDeploymentDir.exists())
            	tempDUJarsDeploymentDir.mkdirs();
            tempDeploymentFile.delete();
            return tempDUJarsDeploymentDir;
            
        } catch (IOException e) {
            logger.error("Temp Deployment Directory could not be created for SLEE DU: " + jarName);
            throw new SLEEException("Failed to create temp deployment dir", e);
        }
    }

    

}