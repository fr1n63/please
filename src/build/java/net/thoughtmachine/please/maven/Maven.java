package net.thoughtmachine.please.maven;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.repository.internal.DefaultArtifactDescriptorReader;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;

public class Maven {

  private final String repository;
  private final List<String> optionalDeps;

  public Maven(String repository, List<String> optionalDeps) {
    this.repository = repository;
    this.optionalDeps = optionalDeps;
  }

  public Set<Artifact> transitiveDependencies(Artifact artifact) {

    RepositorySystem system = newRepositorySystem();

    RepositorySystemSession session = newRepositorySystemSession(system);

    CollectRequest collectRequest = new CollectRequest();
    collectRequest.setRoot(new Dependency(artifact, ""));
    collectRequest.setRepositories(repositories());

    CollectResult collectResult = null;
    try {
      collectResult = system.collectDependencies(session, collectRequest);
    } catch (DependencyCollectionException e) {
      throw new RuntimeException(e);
    }

    PreorderNodeListGenerator visitor = new PreorderNodeListGenerator();
    collectResult.getRoot().accept(visitor);

    Set<Artifact> artifacts = new LinkedHashSet<>();
    for (DependencyNode node : visitor.getNodes()) {
      if (!node.getDependency().isOptional() || optionalDeps.contains(node.getArtifact().getArtifactId())) {
        artifacts.add(node.getArtifact());
      }
    }
    return artifacts;
  }

  private RepositorySystem newRepositorySystem() {
    DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
    locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
    locator.addService(TransporterFactory.class, FileTransporterFactory.class);
    locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
    locator.addService(ArtifactDescriptorReader.class, DefaultArtifactDescriptorReader.class);

    locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
      @Override
      public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
        exception.printStackTrace();
        throw new RuntimeException(exception);
      }
    });

    return locator.getService(RepositorySystem.class);
  }

  public DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system) {
    DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

    LocalRepository localRepo = new LocalRepository("target/local-repo");
    session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

    return session;
  }

  public List<RemoteRepository> repositories() {
    return ImmutableList.of(
      new RemoteRepository.Builder("central", "default", repository)
        .build());
  }
}