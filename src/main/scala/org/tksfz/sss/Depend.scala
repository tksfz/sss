package org.tksfz.sss

object DependencyBuilders {
  final implicit def toGroupID(group: String) = new GroupID(group)
}

final class GroupID private[sss] (groupID: String)
{
	def % (artifactID: String) = groupArtifact(artifactID)

	private def groupArtifact(artifactID: String) =
	{
		new GroupArtifactID(groupID, artifactID)
	}
}
final class GroupArtifactID private[sss] (groupID: String, artifactID: String)
{
	def % (revision: String): ModuleID =
	{
		ModuleID(groupID, artifactID, revision)
	}
}

case class ModuleID(groupId: String, artifactId: String, revision: String)