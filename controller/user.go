package controller

import (
	"dimension_door/logic"
	"dimension_door/models"
	"github.com/gin-gonic/gin"
	"github.com/go-playground/validator/v10"
	"go.uber.org/zap"
)


// LoginHandle 登录请求的api(本接口实现了前端传来code和用户个人信息进行插入数据库和返回token的功能 data：2022/4/6）
// @Summary 用户登录接口
// @Description 完成用户的登录并返回Token
// @Tags 登录
// @Accept application/json
// @Produce application/json
// @Param object query models.Usermsg true "登录令牌"
// @Security ApiKeyAuth
// @Success 200 {object} _ResponseSuccess
// @Router /login [post]
func LoginHandle(c *gin.Context){
	user := &models.Usermsg{
		Username: "username",
		Picture:  "picture",
		Gender:   0,
	}
	//1、获取参数（Code）
	if err := c.ShouldBindJSON(user);err != nil{
		errs,ok :=err.(validator.ValidationErrors) // 类型断言,判断这个错误是否由binding 引发的
		if !ok {
			ResponseError(c,CodeInvalidParam)
			return
		}
		//由binding引发
		ResponseErrorWithMsg(c,CodeInvalidParam,removeTopStruct(errs.Translate(trans)))
		return
	}
	//2、请求微信服务器获取参数
	resp,err := WXlogin(user.Code)
	if err != nil {
		ResponseError(c,CodeServerBusy)
		return
	}
	//使用openid 换取登录状态
	token ,err := logic.Login(resp,user)
	if err != nil {
		zap.L().Error("logic.Login(resp,user) err ",zap.Error(err))
		ResponseError(c,CodeServerBusy)
		return
	}
	//返回数据
	ResponseSuccess(c,CodeSuccess,gin.H{
		"userid":resp.Openid,
		"token":token,
	})
}

// InputUserHandle 录入学生学号、姓名、班级
func InputUserHandle(c *gin.Context) {
	// 获取参数
	p := new(models.StudentMsg)
	err := c.ShouldBindJSON(p)
	if err != nil {
		ResponseError(c, CodeInvalidParam)
		return
	}
	// 取当前用户的openId
	userid, err := getCurrentUser(c)
	if err != nil {
		ResponseError(c, CodeNeedLogin)
		return
	}
	err = logic.InputUser(p, userid)
	if err != nil {
		zap.L().Error("mysql.InputUser err", zap.Error(err))
		ResponseError(c, CodeServerBusy)
		return
	}
	ResponseSuccess(c, CodeSaveSuccess, nil)
}