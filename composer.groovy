/**Plugin JsonComposer
 * @author Jeremy Desmars 
 * Plugin with goal to create, upload and update a json when composer librairy is upload, remove or move.
 */

/**  Globally available variables:
 *
 * log (org.slf4j.Logger)
 * repositories (org.artifactory.repo.Repositories repositories)
 * security (org.artifactory.security.Security security)
 *
 * context (org.artifactory.spring.InternalArtifactoryContext) - NOT A PUBLIC API - FOR INTERNAL USE ONLY!
 */
 /**
 * A section for handling and manipulating download events.
 */


import org.artifactory.fs.FileLayoutInfo
import org.artifactory.fs.FileInfo
import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath
import org.artifactory.repo.RepositoryConfiguration
import org.artifactory.repo.RepoPathFactory
import org.artifactory.build.promotion.PromotionConfig
import org.artifactory.build.staging.ModuleVersion
import org.artifactory.build.staging.VcsConfig
import org.artifactory.exception.CancelException
import org.artifactory.request.Request
import org.artifactory.util.StringInputStream
import org.artifactory.resource.ResourceStreamHandle
import org.artifactory.util.PathUtils

import groovy.json.JsonSlurper
import groovy.json.JsonBuilder

import java.nio.file.Files
import java.io.InputStream

import java.security.MessageDigest

//Setting
def pathSetting = "C:\\artifactoryPro\\data\\tmp\\"

download {

		/**
		* Provide an alternative response, by setting a success/error status code value and an optional error message.
		* Will not be called if the response is already committed (e.g. a previous error occurred).
		* Currently called only for GET requests where the resource was found.
		*
		* Context variables:
		* status (int) - a response status code. Defaults to -1 (unset).
		* message (java.lang.String) - a text message to return in the response body, replacing the response content.
		*                              Defaults to null.
		*
		* Closure parameters:
		* repoPath (org.artifactory.repo.RepoPath) - a read-only parameter of the original request RepoPath.
		*/
		altResponse
		{
			request, responseRepoPath ->
		}

		/**
		* Provides an alternative download path under the same remote repository, by setting a new value to the path
		* variable.
		*
		* Context variables:
		* path (java.lang.String) - the new path value. Defaults to the originalRepoPath's path.
		*
		* Closure parameters:
		* repoPath (org.artifactory.repo.RepoPath) - a read-only parameter of the original request RepoPath.
		*/
		altRemotePath
		{
			repoPath ->
		}

		/**
		* Provide an alternative download content, by setting new values for the inputStream and size context variables.
		*
		* Context variables:
		* inputStream (java.io.InputStream) - a new stream that provides the response content. Defaults to null.
		* size (long) - the size of the new content (helpful for clients processing the response). Defaults to -1.
		*
		* Closure parameters:
		* repoPath (org.artifactory.repo.RepoPath) - a read-only parameter of the original request RepoPath.
		*/
		altRemoteContent
		{
			repoPath ->
		}

		/**
		* In case of resolution error provide an alternative response, by setting a success/error status code value and an optional error message.
		* Will not be called if the response is already committed (e.g. a previous error occurred).
		* As opposite to altResponse, called only for GET requests during which error occurred (e.g. 404 - not found, or 409 - conflict).
		*
		* Context variables:
		* status (int) - a response error status code (may be overridden in the plugin).
		* message (java.lang.String) - a response error message (may be overridden in the plugin).
		* inputStream (java.io.InputStream) - a new stream that provides the response content. Defaults to null.
		* size (long) - the size of the new content (helpful for clients processing the response). Defaults to -1.
		*
		* Closure parameters:
		* request (org.artifactory.request.Request) - a read-only parameter of the request.
		*/
		afterDownloadError
		{
			Request request ->
		}

		/**
		* Handle before remote download events.
		*
		* Context variables:
		* headers (java.util.Map<String,String>) - Map containing the extra headers to insert into the remote server request
		*
		* Usage example:
		* headers = ["ExtraHeader":"SpecialHeader"]
		*
		* Note: The following cannot be used as extra headers and Artifactory will always override them:
		* "X-Artifactory-Originated". "Origin-Artifactory", "Accept-Encoding"
		*
		* Closure parameters:
		* request (org.artifactory.request.Request) - a read-only parameter of the request. [since: 2.3.4]
		* repoPath (org.artifactory.repo.RepoPath) - a read-only parameter of the original request RepoPath.
		*/
		beforeRemoteDownload
		{
			request, repoPath ->
		}

		/**
		* Handle after remote download events.
		*
		* Closure parameters:
		* request (org.artifactory.request.Request) - a read-only parameter of the request. [since: 2.3.4]
		* repoPath (org.artifactory.repo.RepoPath) - a read-only parameter of the original request RepoPath.
		*/
		afterRemoteDownload
		{
			request, repoPath ->
		}

		/**
		* Handle before local download events.
		*
		* Closure parameters:
		* request (org.artifactory.request.Request) - a read-only parameter of the request.
		* responseRepoPath (org.artifactory.repo.RepoPath) - a read-only parameter of the response RepoPath (containing the
		*                                                    physical repository the resource was found in).
		*/
		beforeDownload
		{
			request, responseRepoPath ->

		}
				/**
				* Handle before any download events, at this point the request passed all of Artifactory's filters (authentication etc) and is about to reach the repositories.
				*
				* Context variables:
				* expired (boolean) - Mark the requested resource as expired. Defaults to false (unset).
				*                     An expired resource is one that it's (now() - (last updated time)) time is higher than the repository retrieval cache period milliseconds.
				*                     Setting this option to true should be treated with caution, as it means both another database hit (for updating the last updated time)
				*                     as well as network overhead since if the resource is expired, a remote download will occur to re-download it to the cache.
				*                     A common implementation of this extension point is to check if the resource comply with a certain pattern (for example: a *.json file)
				*                     AND the original request was to the remote repository (and not directly to it's cache)
				*                     AND a certain amount of time has passed since the last expiry check (to minimize DB hits).
				*                     See our public GitHub for an example here: https://github.com/JFrogDev/artifactory-user-plugins/blob/master/download/beforeDownloadRequest.groovy
				*
				* modifiedRepoPath (org.artifactory.repo.RepoPath)
				*                     Forces Artifactory to store the file at the specified repository path in the remote cache.
				*                     See our public GitHub for an example here: https://github.com/JFrogDev/artifactory-user-plugins/blob/master/download/modifyMD5File.groovy
				* Closure parameters:
				* request (org.artifactory.request.Request) - a read-only parameter of the request.
				* repoPath (org.artifactory.repo.RepoPath) -  a read-only parameter of the response RepoPath (containing the
				*                                                    physical repository the resource was found in).
		*/
		beforeDownloadRequest
		{
			request, repoPath ->
		}
	}


storage {

		/**
		* Handle before create events.
		*
		* Closure parameters:
		* item (org.artifactory.fs.ItemInfo) - the original item being created.
		*/
		beforeCreate
		{
			item ->
			def layout = repositories.getRepositoryConfiguration(item.repoPath.repoKey).getRepoLayoutRef()
			if ((layout == "composer-default")&&(item.getMimeType() == "application/zip"))
			{
				deployNewInclude(item.repoPath, pathSetting)
			}

		}

		/**
		* Handle after create events.
		*
		* Closure parameters:
		* item (org.artifactory.fs.ItemInfo) - the original item being created.
		*/
		afterCreate
		{
			item->
			//call createInclude() function if archive are a librairies composer
			def layout = repositories.getRepositoryConfiguration(item.repoPath.repoKey).getRepoLayoutRef()
			if ((layout == "composer-default")&&(item.getMimeType() == "application/zip"))
			{
				createInclude(item, pathSetting )

			}
		}

		/**
		* Handle before delete events.
		*
		* Closure parameters:
		* item (org.artifactory.fs.ItemInfo) - the original item being being deleted.
		*/
		beforeDelete
		{
			item ->

		}

		/**
		* Handle after delete events.
		*
		* Closure parameters:
		* item (org.artifactory.fs.ItemInfo) - the original item deleted.
		*/
		afterDelete
		{
			item ->
			//call createInclude() function if archive are a librairies composer
			def layout = repositories.getRepositoryConfiguration(item.repoPath.repoKey).getRepoLayoutRef()
			if ((layout == "composer-default")&&(item.getMimeType() == "application/zip"))
			{
				removeInclude(item, pathSetting )
			}
		}

		/**
		* Handle before move events.
		*
		* Closure parameters:
		* item (org.artifactory.fs.ItemInfo) - the source item being moved.
		* targetRepoPath (org.artifactory.repo.RepoPath) - the target repoPath for the move.
		* properties (org.artifactory.md.Properties) - user specified properties to add to the item being moved.
		*/
		beforeMove
		{
			item, targetRepoPath, properties ->
			//call updateJsonMove() function if archive are a librairies composer
			def layout = repositories.getRepositoryConfiguration(item.repoPath.repoKey).getRepoLayoutRef()
			if ((layout == "composer-default")&&(item.getMimeType() == "application/zip"))
			{
				updateJsonMove(item, targetRepoPath, pathSetting)
			}
		}

		/**
		* Handle after move events.
		*
		* Closure parameters:
		* item (org.artifactory.fs.ItemInfo) - the source item moved.
		* targetRepoPath (org.artifactory.repo.RepoPath) - the target repoPath for the move.
		* properties (org.artifactory.md.Properties) - user specified properties to add to the item being moved.
		*/
		afterMove
		{
			item, targetRepoPath, properties ->

		}

		/**
		* Handle before copy events.
		*
		* Closure parameters:
		* item (org.artifactory.fs.ItemInfo) - the source item being copied.
		* targetRepoPath (org.artifactory.repo.RepoPath) - the target repoPath for the copy.
		* properties (org.artifactory.md.Properties) - user specified properties to add to the item being moved.
		*/
		beforeCopy
		{
			item, targetRepoPath, properties ->
		}

		/**
		* Handle after copy events.
		*
		* Closure parameters:
		* item (org.artifactory.fs.ItemInfo) - the source item copied.
		* targetRepoPath (org.artifactory.repo.RepoPath) - the target repoPath for the copy.
		* properties (org.artifactory.md.Properties) - user specified properties to add to the item being moved.
		*/
		afterCopy
		{
			item, targetRepoPath, properties ->
		}

		/**
		* Handle before property create events.
		*
		* Closure parameters:
		* item (org.artifactory.fs.ItemInfo) - the item on which the property is being set.
		* name (java.lang.String) - the name of the property being set.
		* values (java.lang.String[]) - A string array of values being assigned to the property.
		*/
		beforePropertyCreate
		{
			item, name, values ->
		}
		/**
		* Handle after property create events.
		*
		* Closure parameters:
		* item (org.artifactory.fs.ItemInfo) - the item on which the property has been set.
		* name (java.lang.String) - the name of the property that has been set.
		* values (java.lang.String[]) - A string array of values assigned to the property.
		*/
		afterPropertyCreate
		{
			item, name, values ->
		}
		/**
		* Handle before property delete events.
		*
		* Closure parameters:
		* item (org.artifactory.fs.ItemInfo) - the item from which the property is being deleted.
		* name (java.lang.String) - the name of the property being deleted.
		*/
		beforePropertyDelete
		{
			item, name ->
		}
		/**
		* Handle after property delete events.
		*
		* Closure parameters:
		* item (org.artifactory.fs.ItemInfo) - the item from which the property has been deleted.
		* name (java.lang.String) - the name of the property that has been deleted.
		*/
		afterPropertyDelete
		{
			item, name ->
		}
	}

	jobs {
	/**
	 * A job definition.
	 * The first value is a unique name for the job.
	 * Job runs are controlled by the provided interval or cron expression, which are mutually exclusive.
	 *
	 * Parameters:
	 * delay (long) - An initial delay in milliseconds before the job starts running (not applicable for a cron job).
	 * interval (long) -  An interval in milliseconds between job runs.
	 * cron (java.lang.String) - A valid cron expression used to schedule job runs (see: http://www.quartz-scheduler.org/docs/tutorial/TutorialLesson06.html)
	 */
	myJob(interval: 30000, delay: 1000) {
		echo ""
	}
}
	
def echo(str) {
	log.warn "##### " + str;
}

/**
 * Generate a temporary Json
 * @param item
 */
private def createJson(item, pathSetting )
{
	//Create a string with the contain of composer.json
													//context.artifactoryPro.data+"\\tmp\\artifactory-uploads\\" 
	def zipFile = new java.util.zip.ZipFile(new File(pathSetting +"/artifactory-uploads\\"+item.name))
	
	def searchstr = "composer.json"
	def find = false
	zipFile.entries().each
	{
		entry->
		if (entry.name == searchstr)
		{
			stringFile = zipFile.getInputStream(entry).text
		}
	}
	
	//Change string in object
	def jsonSlurper = new JsonSlurper()
	def objectFile = jsonSlurper.parseText(stringFile)
	
	//Define variable for JsonMap
	def name = objectFile.name
	def version = ""
	def version_Normalized = ""
	def typefile = ""
	def url = ""
	def reference = ""
	def typeFile = ""
	def urlFile = "http://localhost:8081/artifactory/"+item.repoPath.id
	def shasum = item.sha1
	def require = objectFile.require
	def require_dev = objectFile.get("require-dev")
	def time = ""
	def type = objectFile.type
	def installationsource = ""
	def autoload = objectFile.autoload
	def license = objectFile.license
	def description = objectFile.description
	def homepage = objectFile.homepage
	def keywords =  objectFile.keywords

	/**Recup version in FileName
	*Filename Type:  string-x.x.x-string
	*string = (.*)
	*x = ([0-9]) =  number
	**/
	expression = /(.*)-([0-9]).([0-9]).([0-9])-(.*)/
	matcher = (item.name =~ expression)
	if (matcher.matches())
	{
	   version = matcher[0][2]+"."+matcher[0][3]+"."+matcher[0][4]
		version_Normalized = version+".0"
	}

	//create JsonMap
	def builder = new groovy.json.JsonBuilder()
	def json = new JsonBuilder([name: name, version: version, version_Normalized: version_Normalized, source:[type: typefile, url: url, reference: reference],dist:[type: typeFile, url:urlFile, reference: reference, shasum: shasum],require:require, require_dev: require_dev, time : time, type : type, installation_source: installationsource, autoload:[autoload],license:[ license],description:description, homepage: homepage, keywords:keywords])
			
	//Create a new JsonFile
	new File(pathSetting +'work/tmp_file_Composer.json').delete()
	def composer = new File(pathSetting +'work/tmp_file_Composer.json') << json.toPrettyString()

	return composer
}

/**
 * Generate a new Json include for packages.json
 * @param item
 */
private def createInclude(item, pathSetting )
{
   createInclude(item, pathSetting, item.repoPath )
}

/**
 * Generate a new Json include for packages.json
 * @param item
 * @param targetRepoPath
 */
private def createInclude(item, pathSetting, targetRepoPath)
{
	//Create a string with contain of json in repository include
	list = repositories.getChildren(RepoPathFactory.create(targetRepoPath.repoKey,"/include/"))
	includeString = repositories.getStringContent(list[0])
 	echo includeString
	//create string property
	def property = ""
	property = loadProperties(item.repoPath, "json")
	if (!property)
	{
		// Call createJson() function
		def composer = createJson(item, pathSetting )
		property = ""
		composer.eachLine
		{
			line->
			property = property + line
		}
		propertySetting = property.bytes.encodeBase64().toString()
	}
	else
	{
		propertySetting = property
		property = new String(property.decodeBase64())
	}
	//change string Property to object
	//The actual version of groovy in artifactory is 1.8.8, in 2.2.* or more we can parse file directly (change parseTexte(string) to parse(file))
	def jsonSlurperProperty = new JsonSlurper()
	def objectProperty = jsonSlurperProperty.parseText(property)
	
	//change  includeString to object
	def jsonSlurperInclude = new JsonSlurper()
	def objectInclude = jsonSlurperInclude.parseText(includeString)

	def version = objectProperty.version
	def name = objectProperty.name

	//put objectProperty in objectInclude
	if (!objectInclude.packages.get(name))
	{
		def stringTmp ="{}"
		def jsonSlurperTmp = new JsonSlurper()
		def objectTmp = jsonSlurperTmp.parseText(stringTmp)

		objectTmp.put(version, objectProperty)
		objectInclude.packages.put(name, objectTmp)
	}
	else
	{
		objectInclude.packages.get(name).put(version, objectProperty)
	}

	//create new includeJson and delete old include
	def includeJson = new JsonBuilder(objectInclude)
	def tmpNameFile = "tmp_include_"+targetRepoPath.repoKey
	file =  new File(pathSetting +'work/'+tmpNameFile+'.json') << includeJson.toPrettyString()
	//Create and set property to artifact
	repositories.setProperty item.repoPath, 'json', propertySetting
	def propertySha1 = generateHash(file, 'SHA1', 40)
	repositories.setProperty item.repoPath, 'Sha1', propertySha1

	//deploy file
	deployFile(item,tmpNameFile, targetRepoPath, pathSetting )
	
	//delete temporary json
	new File(pathSetting +'work/'+tmpNameFile+'.json') .delete()
}


/**
*load property artefact
*@param targetRepoPath
*@param nameProperty
**/

private def loadProperties(targetRepoPath,nameProperty)
{
	def properties = repositories.getProperty(targetRepoPath, nameProperty)
	return properties
}

/**
*remove artifact in include
*@param item
**/
private def removeInclude(item,pathSetting )
{

	//load properties artifact
	def property = loadProperties(item.repoPath, "json")
	def propertyDecoded = new String(property.decodeBase64())
	//change propertyDecoded to objectProperty
	def jsonSlurper = new JsonSlurper()
	def objectProperty = jsonSlurper.parseText(propertyDecoded)

	//Create a string with contain of json in repository include
	def nameFileSha1 = "all\$"+nameLastInclude(item.repoPath)
	def include = RepoPathFactory.create(item.repoPath.repoKey+"/include/"+nameFileSha1+".json")
	def includeString = repositories.getStringContent(include)

	//change includeString to object
	def jsonSlurperInclude = new JsonSlurper()
	def objectInclude = jsonSlurperInclude.parseText(includeString)

	def version = objectProperty.version
	def	name = objectProperty.name

	//remove artifact
	if (objectInclude.packages.get(name).size() <=1)
	{
		objectInclude.packages.remove(name)
	}
	else
	{
		objectInclude.packages.get(name).remove(version)
	}

	def includeJson = new JsonBuilder(objectInclude)
	
	//Create temporary json
	def tmpNameFile = "tmp_include_"+item.repoPath.repoKey
	def file =  new File(pathSetting +'work/'+tmpNameFile+'.json') << includeJson.toPrettyString()

	//deploy file
	def targetRepoPath = item.repoPath
	deployFile(item,tmpNameFile, targetRepoPath, pathSetting )
	
	//delete temporary json
	new File(pathSetting +'work/'+tmpNameFile+'.json').delete()
}

/**
 * Deploy files
 * @param item
 * @param tmpNameFile
 * @param targetRepoPath
 */
private def deployFile(item,tmpNameFile, targetRepoPath, pathSetting  )
{
	//delete last include.json
	nameFileSha1 = nameLastInclude(targetRepoPath)
	targetRepoPathDelete = RepoPathFactory.create(targetRepoPath.repoKey,"/include/all\$"+nameFileSha1+".json")
	repositories.delete(targetRepoPathDelete)

	//deploy json
	file = new File(pathSetting +"work/"+tmpNameFile+'.json')
	def nameFile= "all\$"+generateHash(file, 'SHA1', 40)
	targetRepoPath = RepoPathFactory.create(targetRepoPath.repoKey,"/include/"+nameFile+".json")

	
	file.withInputStream { is ->
		repositories.deploy targetRepoPath, is
		is.close();
   }

	//deploy packages.json
	file = createPackagesJson(tmpNameFile, nameFile, pathSetting )
	   targetRepoPath = RepoPathFactory.create(targetRepoPath.repoKey,"/packages.json")
	
	file.withInputStream { is ->
	   repositories.deploy targetRepoPath, is
	   is.close();
   }
}


/**
 * update Json after Move
 * @param item
 * @param  targetRepoPath
 */
private def updateJsonMove(item, targetRepoPath, pathSetting)
{
	//update Json if is composer librairies
		def targetlayout = repositories.getRepositoryConfiguration(targetRepoPath.repoKey).getRepoLayoutRef()
		if (targetlayout == "composer-default")
		{
			createInclude(item, pathSetting, targetRepoPath)
		}
}

/**
 * generate sha1
 * @param file
 * @param hashType
 * @param leftChar
 */
private def generateHash(file, hashType, leftChar)
{
	digest = MessageDigest.getInstance(hashType)
	file.withInputStream()
	{is->
		buffer = new byte[8192]
		read = 0
		while((read = is.read(buffer)) > 0)
		{
				  digest.update(buffer, 0, read);
		}
	}
	hash = digest.digest()
	bigInt = new BigInteger(1, hash)
	return bigInt.toString(16).padLeft(leftChar, '0')
}

/**
 * create packages.json
 * @param nameFile
 * @param nameFileSha1
**/

private def createPackagesJson(nameFile, nameFileSha1, pathSetting  )
{
	file = new File(pathSetting +"work/"+nameFile+'.json')
	//create JsonMap
	def builderPackages = new groovy.json.JsonBuilder()
	def packages = new JsonBuilder([packages:[], includes :["include/$nameFileSha1":[sha1:generateHash(file, "Sha1", 40)]]])

	//Create a new JsonFile
	new File(pathSetting +'work/packages.json').delete()
	def packagesJson = new File(pathSetting +'work/packages.json') << packages.toPrettyString()
	return packagesJson
}

/**
 * find shasum last json deploy in include
 * @param  targetRepoPath
 */
private def nameLastInclude(targetRepoPath)
{
	//create list of file in include
	list = repositories.getChildren(RepoPathFactory.create(targetRepoPath.repoKey,"/include/"))
	string = list[0].repoPath
	
	/**Recup shasum in fileName
	*Filename Type:  string-string-string:string/all$xxx.json
	*string = (.*)  or ([a-zA-Z]+)
	*xxx = ([a-zA-Z-0-9]+) =  alphanumeric string
	**/
	expression =  /(.*):([a-zA-Z]+)(.)([a-zA-Z]+)(\$)([a-zA-Z-0-9]+)(.)([a-zA-Z]+)/
	matcher = ( string =~ expression )


	if (matcher.matches()) {
		sha1 = matcher[0][6]
		return sha1
	}
}

private def deployNewInclude(targetRepoPath, pathSetting)
{
	list = repositories.getChildren(RepoPathFactory.create(targetRepoPath.repoKey,"/include/"))
	echo "test: "+list
	if(list[0] == null)
	{
		includeString = "{'packages':{}}"
		
		def jsonIncludeBuilder = new groovy.json.JsonBuilder()
		def jsonInclude = new JsonBuilder([packages:[]])
		new File(pathSetting +'work/FileInclude.json').delete()
		fileInclude = new File(pathSetting +'work/FileInclude.json') << jsonInclude.toPrettyString()
		nameFile ="all\$"+generateHash(fileInclude, "sha1", 40)
		targetRepoPath = RepoPathFactory.create(targetRepoPath.repoKey,"/"+nameFile+".json")
		
		fileInclude.withInputStream { is ->
		   repositories.deploy targetRepoPath, is
		   is.close();
	   }

	   echo "toto : "+ targetRepoPath
	}
	list = repositories.getChildren(RepoPathFactory.create(targetRepoPath.repoKey,"/include/"))
	echo "test2: "+list
}