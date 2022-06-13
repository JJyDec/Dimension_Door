package controller

import (
	"github.com/gin-gonic/gin"
	"net/http"
)

/*
{
	"code": //程序中的错误码
	"msg": //提示信息
	“data" : //数据
}

*/


type ResponeseCode struct {
	Code ResCode `json:"code"`
	Msg interface{} `json:"msg"`
	Data interface{} `json:"data,omitempty"`
}

// ResponseError 返回一个code 类型的错误
func ResponseError(c *gin.Context ,code ResCode){
	c.JSON(http.StatusOK, &ResponeseCode{
		Code: code,
		Msg: code.Msg(),
		Data: nil,
	})
}

// ResponseErrorWithMsg 返回一个提示信息为自定义的错误
func ResponseErrorWithMsg(c *gin.Context ,code ResCode,msg interface{}){
	c.JSON(http.StatusOK, &ResponeseCode{
		Code: code,
		Msg: msg,
		Data: nil,
	})
}

// ResponseSuccess 返回成功的信息
func ResponseSuccess(c *gin.Context,code ResCode,data interface{}){
	c.JSON(http.StatusOK,&ResponeseCode{
		Code: code,
		Msg: code.Msg(),
		Data: data,
	})
}

