package mysql

import "errors"

var (
	ErrorPostLimit = 		errors.New("报名人数已满")
	ErrorApplyAgain = 		errors.New("请勿重复报名")
	ErrorAffectNotOneMsg = 	errors.New("影响行数不为一条")
)
