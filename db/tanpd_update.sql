--issue 8 March2012
delete t1 from dbeps01.teb_dictionary t1, dbeps01.teb_dictionary t2 where  t1.stKeyword=t2.stKeyword and t1.RecId!=t2.RecId and ((t1.stKeywordType=t2.stKeywordType and t1.RecId>t2.RecId) OR (t1.stKeywordType='N' and t2.stKeywordType!='N') OR (t1.stKeywordType='V' and t2.stKeywordType!='N' and t2.stKeywordType!='V') OR t1.RecId=278730)


--issue 52 March2012
ALTER TABLE `dbeps01`.`schedule` CHANGE `SchFlags` `SchFlags` VARCHAR( 8 ) CHARACTER SET latin1 COLLATE latin1_swedish_ci NOT NULL DEFAULT '0';
UPDATE `dbeps01`.`schedule` SET `SchFlags`='0' WHERE `SchFlags`='';


--issue 55 April2012
UPDATE `dbeps01`.`teb_fields` SET `stDbFieldName` = 'UserDivision' WHERE `teb_fields`.`nmForeignId` =42;
ALTER TABLE `dbeps01`.`users` ADD `UserLaborCategory` VARCHAR( 10 ) NOT NULL DEFAULT '' AFTER `UserDivision`;
UPDATE `dbeps01`.`teb_fields` SET `stDbFieldName` = 'UserLaborCategory' WHERE `teb_fields`.`nmForeignId` =16;

--issue 35 April2012
UPDATE `ebeps01`.`x25user` SET `stPassword`='*3B1FD1671E1353A14FA2B20E97F70A4BB1EF0DBB' WHERE `stPassword`='*3E1DFA47500CCB1882BD912C951259C5A842B17F'


--issue 73,75
UPDATE `dbeps01`.`teb_fields` SET `nmDataType` = '9' WHERE `teb_fields`.`nmForeignId` =231 OR `teb_fields`.`nmForeignId` =271;

--issue 36
INSERT INTO `dbeps01`.`teb_reportcolumns` (
`nmCustomReportId` ,`nmFieldId` ,`stShow` ,`nmOrder` ,`stShort` ,`nmWidth` ,`stClass` ,`stCustom`
)
VALUES ('11', '873', 'Y', '8', '', '10', 'null', ''),('11', '884', 'Y', '9', '', '3', 'null', ''),('11', '883', 'Y', '10', '', '20', 'null', ''),('11', '880', 'Y', '11', '', '45', 'null', '');

INSERT INTO `dbeps01`.`triggers` (`RecId` ,`nmTasktype` ,`ContactList` ,`TriggerName` ,`TriggerEvent` ,`Communication`) VALUES (23 , '1', '1,2,3', 'Missing Business Analyst', 'Enabled', 'No');

DELETE FROM `dbeps01`.`teb_reportcolumns` WHERE `teb_reportcolumns`.`nmCustomReportId` = 31 AND `teb_reportcolumns`.`nmFieldId` = 2
DELETE FROM `dbeps01`.`teb_reportcolumns` WHERE `teb_reportcolumns`.`nmCustomReportId` = 9 AND `teb_reportcolumns`.`nmFieldId` = 116
UPDATE `dbeps01`.`teb_fields` SET `stDbFieldName` = 'nmPercent' WHERE `teb_fields`.`nmForeignId` =1003
UPDATE `dbeps01`.`teb_fields` SET `stDbFieldName` = 'ProjectName' WHERE `teb_fields`.`nmForeignId` =1001;


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
UPDATE `dbeps01`.`teb_customreport` SET `nmDefaultReport` = '1' WHERE `teb_customreport`.`RecId` IN (1,9,15,16,11,25,24,26,10,20,17,27,28,29,19,12,18,14,30,4,2,87);

--dump users in xls
INSERT INTO `dbeps01`.`teb_division` (`nmDivision`, `nmUsersInDivision`, `nmHoursPerDay`, `dtStartTime`, `nmExchangeRate`, `stCountry`, `stHolidays`, `stMoneySymbol`, `nmBurdenFactor`, `stDivisionName`, `dtExchangeRate`, `stCurrency`, `stWorkDays`) 
									VALUES (NULL, '0', '8', '07:00', '1', 'US', '', '$', '1', 'San Francisco', sysdate(), 'USD', 'Mon,Tue,Wed,Thu,Fri');
									
UPDATE  `dbeps01`.`teb_fields` SET  `nmFlags` =  '0' WHERE  `teb_fields`.`nmForeignId` =816;

ALTER TABLE  `dbeps01`.`options` ADD  `FixedDateThreshold` INT NOT NULL DEFAULT  '5';
INSERT INTO  `dbeps01`.`teb_fields` (
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
INSERT INTO  `dbeps01`.`teb_epsfields` (
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