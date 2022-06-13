package redis


// 用作处理社区号和其中文的转化

const (
	NotType=           	"暂未确定类型"
	TypeInnovation =    "三创能力教育类"
	TypeArtsSports=    	"美体美育教育类"
	TypeThoughtGrowth= 	"思想成长教育类"
	TypePractice=     	"实践公益教育类"
)


var Typemsg = map[string]int64{
	NotType:0,
	TypeInnovation: 1 ,
	TypeArtsSports:2,
	TypeThoughtGrowth:3,
	TypePractice:4,
}

func GetMsg(t string)int64{
	msg,ok := Typemsg[t]
	if !ok {
		return Typemsg[NotType]
	}
	return msg
}