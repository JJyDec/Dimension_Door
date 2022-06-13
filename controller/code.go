package controller


//定义一些可能出现的错误码

type ResCode int64
const(
	CodeSuccess ResCode = 1000 +iota
	CodeApplySuccess
	CodeLoginSuccess
	CodePostSuccess
	CodeRemindSuccess
	CodeCancelRemindSuccess
	CodeNeedAdmin
	CodePostLimit
	CodeNotApplyAgain
	CodeDeleteFailed
	CodeDeleteSuccess
	CodeCancelSuccess
	CodeInvalidParam
	CodeUserExist
	CodeUserNotExist
	CodeinvalidPassword
	CodeServerBusy

	CodeNeedLogin
	CodeInvalidToken
	CodeSaveSuccess
)

var codeMsgMap = map[ResCode]string{
	CodeSuccess:				"success",
	CodeApplySuccess:			"报名成功",
	CodeLoginSuccess:			"登录成功",
	CodePostSuccess:			"发布成功",
	CodeRemindSuccess:			"设置提醒成功",
	CodeCancelRemindSuccess:	"取消提醒成功",
	CodeDeleteFailed:			"删除失败",
	CodeDeleteSuccess:			"删除成功",
	CodeCancelSuccess:			"取消成功",
	CodeNeedAdmin:				"需要管理员权限",
	CodePostLimit:				"报名人数达到上限",
	CodeNotApplyAgain:			"请勿重复报名",
	CodeInvalidParam:			"请求参数错误",
	CodeUserExist:				"用户名已存在",
	CodeUserNotExist:			"用户名不存在",
	CodeinvalidPassword:		"用户名或密码错误",
	CodeServerBusy:				"服务繁忙",
	CodeNeedLogin:				"需要登录",
	CodeInvalidToken:			"无效Token",
	CodeSaveSuccess:         	"保存成功",
}

// Msg 返回特定的错误提示信息
func (c ResCode)Msg()string{
	msg,ok := codeMsgMap[c]
	if !ok{
		return codeMsgMap[CodeServerBusy]
	}
	return msg
}