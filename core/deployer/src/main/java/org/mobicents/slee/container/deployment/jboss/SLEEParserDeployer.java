package org.mobicents.slee.container.deployment.jboss;

import org.jboss.deployers.spi.deployer.DeploymentStages;
import org.jboss.deployers.structure.spi.DeploymentUnit;
import org.jboss.deployers.vfs.spi.deployer.AbstractVFSParsingDeployer;
import org.jboss.deployers.vfs.spi.structure.VFSDeploymentUnit;
import org.jboss.logging.Logger;
import org.jboss.virtual.VirtualFile;

public class SLEEParserDeployer extends AbstractVFSParsingDeployer<SLEEDeploymentMetaData>
{

  private static Logger logger = Logger.getLogger( SLEEParserDeployer.class );

  public SLEEParserDeployer()
  {
    super(SLEEDeploymentMetaData.class);
    setSuffix(".jar");
    setStage(DeploymentStages.DESCRIBE);
    
    logger.info("����� SLEE Parser Deployer Initialized! �����");
  }

  protected SLEEDeploymentMetaData parse(VFSDeploymentUnit vfsDU, VirtualFile virtualFile, SLEEDeploymentMetaData sdmd) throws Exception
  {    
    //logger.info("��� SLEEParserDeployer �� protected SLEEDeploymentMetaData parse(VFSDeploymentUnit vfsDU, VirtualFile virtualFile, SLEEDeploymentMetaData sdmd)");
    
    return new SLEEDeploymentMetaData(vfsDU);
  }

  protected SLEEDeploymentMetaData parse(DeploymentUnit du, String str1, String str2, SLEEDeploymentMetaData sdmd) throws Exception
  {
    //logger.info("��� SLEEParserDeployer �� protected SLEEDeploymentMetaData parse(VFSDeploymentUnit vfsDU, VirtualFile virtualFile, SLEEDeploymentMetaData sdmd)");

    if(logger.isTraceEnabled())
    {
      logger.trace("SLEEParserDeployer 'parse' called:");
      logger.trace("du................." + du);
      logger.trace("str1..............." + str1);
      logger.trace("str2..............." + str2);
      logger.trace("sdmd..............." + sdmd);
    }

    SLEEDeploymentMetaData _sdmd = new SLEEDeploymentMetaData(du);

    if(_sdmd.componentType == SLEEDeploymentMetaData.ComponentType.DU)
    {
      return _sdmd;
    }

    return null;
  }
}