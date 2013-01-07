--issue 8 March2012
delete t1 from teb_dictionary t1, teb_dictionary t2 where  t1.stKeyword=t2.stKeyword and t1.RecId!=t2.RecId and ((t1.stKeywordType=t2.stKeywordType and t1.RecId>t2.RecId) OR (t1.stKeywordType='N' and t2.stKeywordType!='N') OR (t1.stKeywordType='V' and t2.stKeywordType!='N' and t2.stKeywordType!='V') OR t1.RecId=278730)


--issue 52 March2012
ALTER TABLE `schedule` CHANGE `SchFlags` `SchFlags` VARCHAR( 8 ) CHARACTER SET latin1 COLLATE latin1_swedish_ci NOT NULL DEFAULT '0';
UPDATE `schedule` SET `SchFlags`='0' WHERE `SchFlags`='';


--issue 55 April2012
UPDATE `teb_fields` SET `stDbFieldName` = 'UserDivision' WHERE `teb_fields`.`nmForeignId` =42;
ALTER TABLE `users` ADD `UserLaborCategory` VARCHAR( 10 ) NOT NULL DEFAULT '' AFTER `UserDivision`;
UPDATE `teb_fields` SET `stDbFieldName` = 'UserLaborCategory' WHERE `teb_fields`.`nmForeignId` =16;

--issue 35 April2012
UPDATE `ebeps01`.`x25user` SET `stPassword`='*3B1FD1671E1353A14FA2B20E97F70A4BB1EF0DBB' WHERE `stPassword`='*3E1DFA47500CCB1882BD912C951259C5A842B17F'


--issue 73,75
UPDATE `teb_fields` SET `nmDataType` = '9' WHERE `teb_fields`.`nmForeignId` =231 OR `teb_fields`.`nmForeignId` =271;

--issue 36
INSERT INTO `teb_reportcolumns` (
`nmCustomReportId` ,`nmFieldId` ,`stShow` ,`nmOrder` ,`stShort` ,`nmWidth` ,`stClass` ,`stCustom`
)
VALUES ('11', '873', 'Y', '8', '', '10', 'null', ''),('11', '884', 'Y', '9', '', '3', 'null', ''),('11', '883', 'Y', '10', '', '20', 'null', ''),('11', '880', 'Y', '11', '', '45', 'null', '');

INSERT INTO `triggers` (`RecId` ,`nmTasktype` ,`ContactList` ,`TriggerName` ,`TriggerEvent` ,`Communication`) VALUES (23 , '1', '1,2,3', 'Missing Business Analyst', 'Enabled', 'No');

DELETE FROM `teb_reportcolumns` WHERE `teb_reportcolumns`.`nmCustomReportId` = 31 AND `teb_reportcolumns`.`nmFieldId` = 2
DELETE FROM `teb_reportcolumns` WHERE `teb_reportcolumns`.`nmCustomReportId` = 9 AND `teb_reportcolumns`.`nmFieldId` = 116
UPDATE `teb_fields` SET `stDbFieldName` = 'nmPercent' WHERE `teb_fields`.`nmForeignId` =1003
UPDATE `teb_fields` SET `stDbFieldName` = 'ProjectName' WHERE `teb_fields`.`nmForeignId` =1001;


--workflow
CREATE TABLE `test`.`teb_workflow` (
`nmProjectId` int( 11 ) NOT NULL ,
`nmBaseLine` int( 11 ) NOT NULL ,
`nmSchId` int( 11 ) NOT NULL ,
`SchEstimatedEffort` double NOT NULL DEFAULT '0',
`SchEfforttoDate` double NOT NULL DEFAULT '0',
`SchDone` char( 1 ) DEFAULT NULL ,
`nmUserId` int( 11 ) NOT NULL ,
`nmStatus` int( 11 ) NOT NULL ,
PRIMARY KEY ( `nmProjectId` , `nmBaseLine` , `nmSchId` )
) ENGINE = InnoDB DEFAULT CHARSET = latin1;

--report name
ALTER TABLE `teb_reports` ADD `stReportName` VARCHAR( 255 ) NOT NULL DEFAULT '' AFTER `nmCustomReportId`;
UPDATE `teb_reports` r SET r.stReportName=(SELECT stReportName FROM teb_customreport rc WHERE rc.RecId=r.nmCustomReportId);
ALTER TABLE `teb_customreport` ADD `nmDefaultReport` INT NOT NULL DEFAULT '0';
UPDATE `teb_customreport` SET `nmDefaultReport` = '1' WHERE `teb_customreport`.`RecId` IN (1,9,15,16,11,25,24,26,10,20,17,27,28,29,19,12,18,14,30,4,2,87);

--dump users in xls
INSERT INTO `teb_division` (`nmDivision`, `nmUsersInDivision`, `nmHoursPerDay`, `dtStartTime`, `nmExchangeRate`, `stCountry`, `stHolidays`, `stMoneySymbol`, `nmBurdenFactor`, `stDivisionName`, `dtExchangeRate`, `stCurrency`, `stWorkDays`) 
									VALUES (NULL, '0', '8', '07:00', '1', 'US', '', '$', '1', 'San Francisco', sysdate(), 'USD', 'Mon,Tue,Wed,Thu,Fri');
									
UPDATE  `teb_fields` SET  `nmFlags` =  '0' WHERE  `teb_fields`.`nmForeignId` =816;

ALTER TABLE  `options` ADD  `FixedDateThreshold` INT NOT NULL DEFAULT  '5';
INSERT INTO  `teb_fields` (
`nmForeignId` ,
`stDbFieldName` ,
`nmDataType` ,
`nmFlags` ,
`nmOrder2` ,
`nmGrouping` ,
`nmGroupingOrder` ,
`stDefaultValue` ,
`nmTabId` ,
`stTabName` ,
`stLabel` ,
`nmOrder` ,
`nmHeaderOrder` ,
`nmMinBytes` ,
`nmMaxBytes` ,
`nmRows` ,
`nmCols` ,
`nmSecurityFlags` ,
`stMask` ,
`stValidation` ,
`stValidParam` ,
`stLabelShort`
)
VALUES (
NULL ,  'FixedDateThreshold',  '1',  '1',  '0',  '15',  '15',  '5',  '15',  '',  'Fixed Date Threshold (%)',  '0',  '0',  '0',  '5',  '0',  '5',  '0',  '',  '',  '',  ''
);
INSERT INTO  `teb_epsfields` (
`nmForeignId` ,
`nmOrderDisplay` ,
`stValidationFlags` ,
`stHandler` ,
`stChoiceValues` ,
`stSpecial` ,
`stTemp` ,
`nmPriv` ,
`stNull` ,
`stExtra`
)
VALUES (
'1096',  '999',  'int',  '',  '',  '', NULL ,  '32',  '',  ''
);


UPDATE  `teb_fields` SET  `nmDataType` =  '9' WHERE  `teb_fields`.`nmForeignId` =94 OR `teb_fields`.`nmForeignId`=135;
UPDATE  `teb_epsfields` SET `stValidationFlags` = 'int' WHERE  `teb_epsfields`.`nmForeignId` =94 OR `teb_epsfields`.`nmForeignId`=135;
INSERT INTO  `teb_choices` (
`UniqIdChoice` ,
`stChoiceValue` ,
`nmFieldId`
)
VALUES ('5', '5', '94'),('10', '10', '94'),('20', '20', '94'),('50', '50', '94'),('100', '100', '94'), ('5', '5', '135'),('10', '10', '135'),('20', '20', '135'),('50', '50', '135'),('100', '100', '135');

UPDATE  `teb_fields` SET  `nmMaxBytes` =  '100',`nmCols` =  '100' WHERE  `teb_fields`.`nmForeignId` =3;
UPDATE  `teb_fields` SET  `nmMaxBytes` =  '100',`nmCols` =  '100' WHERE  `teb_fields`.`nmForeignId` =4;


UPDATE `ebeps01`.`x25refcontent` SET `stContent` = '<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html>
    <head>
        <title>EPPORA</title>
        <meta content="by MyInfo.com Robert Eder" name="GENERATOR" />
<style>
#content,#footer,#header .pagetitle,#header-content {
width: PageWidthPx;
}
</style>

<link rel="stylesheet" href="./common/calendar.css" />
<link rel="stylesheet" href="./common/eppora.css" />
<script language="JavaScript" src="./common/jquery-1.7.1.min.js"></script>
<script language="JavaScript" src="./common/jquery-ui-1.8.18.custom.min.js"></script>
<SCRIPT LANGUAGE="JavaScript" SRC="./common/ebmain.js"></SCRIPT>
<script language="JavaScript" src="./common/calendar_us.js"></script>
<script language="JavaScript" src="./common/jQueryRotate.2.2.js"></script>
<script language="JavaScript" src="./common/eppora.js"></script>
</head>
<body id="ebbody" onLoad="EpsLoad();" class="~BodyStyleClass~">
<layer name="nsviewer" bgcolor="#cccccc" width=0 height=0 style="border-width:thin;z-index:1"></layer>
<script language="JavaScript1.2">
if (iens6)
{
 document.write("<div id=''viewer'' name=''viewer'' style=''background-color:#cccccc;marginleft:0;visibility:hidden;position:absolute;width:2;height:2;zindex:1;overflow:hidden''></div>");
}
if (ns4)
{
 hideobj = eval("document.nsviewer");
 hideobj.visibility="hidden";
}
</script>' WHERE `x25refcontent`.`nmContentId` = 1;

UPDATE `ebeps01`.`x25refcontent` SET `stContent`='<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\"\r\n\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\r\n<html>\r\n    <head>\r\n        <title>EPPORA</title>\r\n        <meta content=\"by MyInfo.com Robert Eder\" name=\"GENERATOR\" />\r\n<style>\r\n#content,#footer,#header .pagetitle,#header-content,#header .hbar, #header-wrap {\r\nwidth: ~PageWidthPx~px;\r\n}\r\n</style>\r\n\r\n<link rel=\"stylesheet\" href=\"./common/calendar.css\" />\r\n<link rel=\"stylesheet\" href=\"./common/eppora.css\" />\r\n<script language=\"JavaScript\" src=\"./common/jquery-1.7.1.min.js\"></script>\r\n<script language=\"JavaScript\" src=\"./common/jquery-ui-1.8.18.custom.min.js\"></script>\r\n<SCRIPT LANGUAGE=\"JavaScript\" SRC=\"./common/ebmain.js\"></SCRIPT>\r\n<script language=\"JavaScript\" src=\"./common/calendar_us.js\"></script>\r\n<script language=\"JavaScript\" src=\"./common/jQueryRotate.2.2.js\"></script>\r\n<script language=\"JavaScript\" src=\"./common/eppora.js\"></script>\r\n</head>\r\n<body id=\"ebbody\" onLoad=\"EpsLoad();\" class=\"~BodyStyleClass~\">\r\n<layer name=\"nsviewer\" bgcolor=\"#cccccc\" width=0 height=0 style=\"border-width:thin;z-index:1\"></layer>\r\n<script language=\"JavaScript1.2\">\r\nif (iens6)\r\n{\r\n document.write(\"<div id=\'viewer\' name=\'viewer\' style=\'background-color:#cccccc;marginleft:0;visibility:hidden;position:absolute;width:2;height:2;zindex:1;overflow:hidden\'></div>\");\r\n}\r\nif (ns4)\r\n{\r\n hideobj = eval(\"document.nsviewer\");\r\n hideobj.visibility=\"hidden\";\r\n}\r\n</script>' WHERE `nmContentId`='1';


ALTER TABLE  `schedule` ADD  `SchStatus` ENUM(  'Not Started',  'In Progress', 'Done',  'Approved',  'Suspended' ) NOT NULL DEFAULT  'Not Started' AFTER  `SchDone`;
INSERT INTO `teb_fields` (`nmForeignId`, `stDbFieldName`, `nmDataType`, `nmFlags`, `nmOrder2`, `nmGrouping`, `nmGroupingOrder`, `stDefaultValue`, `nmTabId`, `stTabName`, `stLabel`, `nmOrder`, `nmHeaderOrder`, `nmMinBytes`, `nmMaxBytes`, `nmRows`, `nmCols`, `nmSecurityFlags`, `stMask`, `stValidation`, `stValidParam`, `stLabelShort`) VALUES (NULL, 'SchStatus', '9', '3', '0', '21', '21', 'Not Started', '21', '', 'Status', '0', '0', '2', '255', '0', '64', '0', '', '', '', '');
INSERT INTO `teb_epsfields` (`nmForeignId`, `nmOrderDisplay`, `stValidationFlags`, `stHandler`, `stChoiceValues`, `stSpecial`, `stTemp`, `nmPriv`, `stNull`, `stExtra`) VALUES (ID, '50', '', '', '', NULL, NULL, '224', '', '');
INSERT INTO `teb_choices` (`RecId`, `UniqIdChoice`, `nmParentId`, `UniqIdField`, `stChoiceValue`, `nmOrder`, `stReference`, `stWPText`, `nmFieldId`, `nmPrivUserReadFlags`, `nmPrivRecordFlags`, `stChoiceCustomer`, `stChoiceValueShort`, `stChoiceValueAllow`, `stChoiceDependancy`) VALUES (NULL, 'Not Started', '0', NULL, 'Not Started', '10', '', '', ID, '0', '0', '', '', '', ''), (NULL, 'In Progress', '0', NULL, 'In Progress', '20', '', '', ID, '0', '0', '', '', '', ''), (NULL, 'Approved', '0', NULL, 'Approved', '30', '', '', ID, '0', '0', '', '', '', ''), (NULL, 'Suspended', '0', NULL, 'Suspended', '40', '', '', ID, '0', '0', '', '', '', '');

ALTER TABLE  `schedule` ADD  `SchRemainingHours` DOUBLE NOT NULL DEFAULT  '0' AFTER  `SchStatus`;
INSERT INTO `teb_fields` (`nmForeignId`, `stDbFieldName`, `nmDataType`, `nmFlags`, `nmOrder2`, `nmGrouping`, `nmGroupingOrder`, `stDefaultValue`, `nmTabId`, `stTabName`, `stLabel`, `nmOrder`, `nmHeaderOrder`, `nmMinBytes`, `nmMaxBytes`, `nmRows`, `nmCols`, `nmSecurityFlags`, `stMask`, `stValidation`, `stValidParam`, `stLabelShort`) VALUES (NULL, 'SchRemainingHours', '31', '1', '0', '21', '21', '0', '21', '', 'Remaining Hours', '0', '0', '0', '5', '0', '5', '0', '', '', '', '');
INSERT INTO `teb_epsfields` (`nmForeignId`, `nmOrderDisplay`, `stValidationFlags`, `stHandler`, `stChoiceValues`, `stSpecial`, `stTemp`, `nmPriv`, `stNull`, `stExtra`) VALUES (ID, '120', '', '', '', NULL, NULL, '224', '', '');


 CREATE  TABLE  `teb_workflow2` (  `nmProjectId` int( 11  )  NOT  NULL ,
 `nmBaseline` int( 11  )  NOT  NULL ,
 `RecId` int( 11  )  NOT  NULL ,
 `SchHolidays` int( 11  )  NOT  NULL DEFAULT  '0',
 `nmD53Flags` int( 11  )  NOT  NULL DEFAULT  '0',
 `nmSchAanalFlags` int( 11  )  NOT  NULL DEFAULT  '0',
 `nmExpenditureToDate` double NOT  NULL DEFAULT  '0',
 `dtSchLastUpdate` datetime  DEFAULT NULL ,
 `SchTitle` varchar( 255  )  NOT  NULL DEFAULT  '',
 `SchParentRecId` int( 11  )  NOT  NULL DEFAULT  '0',
 `SchLevel` int( 11  )  NOT  NULL DEFAULT  '0',
 `SchPctDone` double NOT  NULL DEFAULT  '0',
 `SchFixedFinishDate` datetime  DEFAULT NULL ,
 `SchFixedStartDate` datetime  DEFAULT NULL ,
 `SchComments` longtext,
 `SchCost` double NOT  NULL DEFAULT  '0',
 `SchDependencies` varchar( 64  )  NOT  NULL DEFAULT  '',
 `SchDescription` longtext,
 `SchEfforttoDate` double NOT  NULL DEFAULT  '0',
 `SchEstimatedEffort` double NOT  NULL DEFAULT  '0',
 `SchFinishDate` datetime  DEFAULT NULL ,
 `SchFlags` varchar( 8  )  NOT  NULL DEFAULT  '0',
 `SchWeekendDays` int( 11  )  NOT  NULL DEFAULT  '0',
 `SchPriority` int( 11  )  NOT  NULL DEFAULT  '0',
 `SchRemainingCost` double NOT  NULL DEFAULT  '0',
 `SchLaborCategories` varchar( 255  )  NOT  NULL DEFAULT  '',
 `SchSlack` double NOT  NULL DEFAULT  '0',
 `SchStartDate` datetime  DEFAULT NULL ,
 `SchSuccessors` varchar( 64  )  NOT  NULL DEFAULT  '',
 `SchId` int( 11  )  NOT  NULL DEFAULT  '0',
 `SchInventory` varchar( 64  )  NOT  NULL DEFAULT  '',
 `SchOtherResources` varchar( 64  )  NOT  NULL DEFAULT  '',
 `nmEffort40Flag` int( 11  )  DEFAULT NULL ,
 `SchDone` char( 1  )  DEFAULT NULL ,
 `SchStatus` enum(  'Not Started',  'In Progress', 'Done'  'Approved',  'Suspended'  )  NOT  NULL DEFAULT  'Not Started',
 `SchRemainingHours` double NOT  NULL DEFAULT  '0',
 `SchMessage` char( 1  )  DEFAULT NULL ,
 `lowlvl` int( 11  )  DEFAULT NULL ,
 `nmUserId` INT ( 11 ) NOT NULL,
 `iHandleFlags` INT( 11 ) NOT NULL DEFAULT  '0',
 PRIMARY  KEY (  `nmProjectId` ,  `nmBaseline` ,  `RecId`  ) ,
 KEY  `baseid` (  `nmProjectId` ,  `nmBaseline` ,  `SchId`  ) ,
 KEY  `recid2` (  `RecId`  ) ,
 KEY  `schid` (  `SchId`  )  ) ENGINE  = InnoDB  DEFAULT CHARSET  = latin1;
 
 --1/6/2013
 UPDATE `teb_choices` SET `nmOrder`=nmOrder + 10 WHERE `nmFieldId`=1097 and `nmOrder` >=30;
 INSERT INTO `teb_choices` (`RecId`, `UniqIdChoice`, `nmParentId`, `UniqIdField`, `stChoiceValue`, `nmOrder`, `stReference`, `stWPText`, `nmFieldId`, `nmPrivUserReadFlags`, `nmPrivRecordFlags`, `stChoiceCustomer`, `stChoiceValueShort`, `stChoiceValueAllow`, `stChoiceDependancy`) VALUES (NULL, 'Done', '0', NULL, 'Done', '30', '', '', '1097', '0', '0', '', '', '', '');
 