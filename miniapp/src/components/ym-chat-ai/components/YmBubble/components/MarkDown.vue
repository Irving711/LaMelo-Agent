<template>
	<view class="markdown-wrapper">
		<!-- 思考过程 -->
		<view v-if="thinkContent" class="markdown-think">
			<view class="think-header" @click="toggleThinkContent">
				<view class="think-icon">💭</view>
				<view class="think-title">思考过程</view>
				<view class="think-toggle">{{ isThinkCollapsed ? '▲' : '▼' }}</view>
			</view>
			<view v-if="!isThinkCollapsed" class="think-content" v-html="parsedThinkContent"></view>
		</view>
		<!-- 聊天内容 -->
		<view v-if="chatContent" v-html="parsedContent" class="markdown-content"></view>
		<!--  资源引用 -->
		<view class="retriever" v-if="resources.length > 0">
			<view class="retriever-title">引用</view>
			<view class="retriever-files">
				<view class="retriever-resource-item" v-for="resource in resources" :key="resource.document_name">
					<view class="resource-icon">📄</view>
					<view class="resource-name" @click="checkDetail(resource)">{{ resource.document_name }}</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script setup>
import { marked } from 'marked'
import { defineProps, computed, onMounted, onUnmounted, ref, watch } from 'vue'

const props = defineProps({
	content: { type: String, default: '' },
	resources: { type: Array, default: [] }
})

const emit = defineEmits(['open-reference'])

function checkDetail(resource) {
	emit('open-reference', resource)
}

//自定义标签css
function customStyle(html) {
	// 匹配完整的 img 标签并添加 class
	let newHtml = html.replace(/<img([^>]*?)>/g, '<img$1 class="ym-tag-image">');
	// 匹配 td 标签并添加 class
	newHtml = newHtml.replace(/<td([^>]*?)>/g, '<td$1 class="ym-tag-td">');
	// 匹配 th 标签并添加 class
	newHtml = newHtml.replace(/<th([^>]*?)>/g, '<th$1 class="ym-tag-th">');
	// 匹配 table 标签并添加 class
	newHtml = newHtml.replace(/<table([^>]*?)>/g, '<table$1 class="ym-tag-table">');
	// 匹配 pre 标签并添加 class
	newHtml = newHtml.replace(/<pre([^>]*?)>/g, '<pre$1 class="ym-tag-pre">');
	return newHtml;
}

// 配置marked
marked.setOptions({
	breaks: true,
	gfm: true,
	pedantic: false
})

// 拆分思考过程和聊天内容
const thinkContent = ref('')
const chatContent = ref('')
const isThinkCollapsed = ref(false)
const thinkId = ref('')

// 处理内容拆分
const processContent = () => {
	if (!props.content) return

	let content = props.content

	// 提取思考过程内容
	const thinkMatch = content.match(/<think>([\s\S]*?)<\/think>/)
	if (thinkMatch) {
		thinkContent.value = thinkMatch[1].trim()
		// 检测到完整的think标签，自动折叠
		isThinkCollapsed.value = true
		// 移除思考过程标签，保留其他内容
		content = content.replace(/<think>[\s\S]*?<\/think>/, '')
	} else if (content.includes('<think>')) {
		// 处理未闭合的思考过程，保持展开状态
		const thinkStart = content.indexOf('<think>')
		thinkContent.value = content.substring(thinkStart + 7)
		content = content.substring(0, thinkStart)
		// 未闭合的思考过程保持展开
		isThinkCollapsed.value = false
	}

	chatContent.value = content.trim()
	thinkId.value = `think-${Math.random().toString(36).substr(2, 9)}`
}

// 解析思考过程内容
const parsedThinkContent = computed(() => {
	if (!thinkContent.value) return ''
	return marked.parse(thinkContent.value)
})

// 解析聊天内容
const parsedContent = computed(() => {
	if (!chatContent.value) return ''
	return customStyle(marked.parse(chatContent.value))
})

// 切换思考过程显示状态
const toggleThinkContent = () => {
	isThinkCollapsed.value = !isThinkCollapsed.value
}

// 监听内容变化
watch(() => props.content, () => {
	processContent()
}, { immediate: true })

// 组件挂载时处理内容
onMounted(() => {
	processContent()
})
</script>

<style lang="less">
.markdown-content {
	box-sizing: border-box;
	width: 100%;
	margin: 0;
	padding: 0;
	
	/* 图片样式 */
	.ym-tag-image {
		width: 100% !important;
		height: auto !important;
		display: block !important;
		margin: 20rpx auto;
		box-sizing: border-box;
	}

	/* 表格样式 */
	.ym-tag-table {
		width: 100%;
		max-width: 100%;
		margin: 20rpx 0;
		font-size: 24rpx;
		background: #fff;
		border-radius: 8rpx;
		border-collapse: collapse;
		border: 2rpx solid #e9ecef;
		box-sizing: border-box;
		table-layout: fixed;
		word-wrap: break-word;
		word-break: break-all;
	}

	.ym-tag-th {
		font-weight: 600;
		color: #333;
		padding: 12rpx 8rpx;
		border-bottom: 2rpx solid #e9ecef;
		background: #f8f9fa;
		box-sizing: border-box;
		word-wrap: break-word;
		word-break: break-all;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		max-width: 0;
	}

	.ym-tag-td {
		padding: 12rpx 8rpx;
		border-bottom: 2rpx solid #e9ecef;
		color: #555;
		border-right: 2rpx solid #e9ecef;
		box-sizing: border-box;
		word-wrap: break-word;
		word-break: break-all;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		max-width: 0;
	}

	/* 代码块样式 */
	.ym-tag-pre {
		background: #f7f7f7;
		border-radius: 8rpx;
		text-wrap: wrap;
		white-space: pre-wrap;
		padding: 10rpx;
		box-sizing: border-box;
		width: 100%;
		overflow-x: auto;
	}
}
</style>


<style lang="less" scoped>
.markdown-wrapper {
	.markdown-think {
		border-radius: 12rpx;
		margin: 10rpx 0;
		overflow: hidden;
		background: #f7f7f7;

		.think-header {
			padding: 14rpx 16rpx;
			display: flex;
			align-items: center;
			gap: 8rpx;
			font-size: 24rpx;
			color: #495057;
			font-weight: 600;
			cursor: pointer;
			user-select: none;
			transition: all 0.3s ease;

			.think-icon {
				font-size: 26rpx;
			}

			.think-title {
				flex: 1;
			}

			.think-toggle {
				font-size: 20rpx;
				color: #6c757d;
				transition: transform 0.3s ease;
			}
		}

		.think-content {
			padding: 0 16rpx 16rpx;
			font-size: 26rpx;
			color: #999;
		}
	}

	.markdown-content {
		margin-top: 10rpx;
		font-size: 26rpx;
	}
}


.retriever {
	margin-top: 20rpx;

	.retriever-title {
		font-size: 26rpx;
		font-weight: 500;
		color: #333;
		margin-bottom: 10rpx;
		position: relative;
		margin-top: 30rpx;

		&::before {
			content: "";
			position: absolute;
			left: 60rpx;
			right: 0;
			top: 50%;
			height: 1px;
			background-color: #333;
		}
	}


	.retriever-files {
		display: flex;
		flex-wrap: wrap;
		gap: 12rpx;
		width: 100%;
	}

	.retriever-resource-item {
		display: flex;
		align-items: center;
		gap: 12rpx;
		padding: 12rpx;
		background: #ffffff;
		border-radius: 12rpx;
		border: 1px solid #f1f5f9;
		transition: background-color 0.2s ease, border-color 0.2s ease;
		min-width: 0; // 允许内部内容收缩，避免撑开

		.resource-icon {
			font-size: 24rpx;
			flex-shrink: 0;
		}

		.resource-name {
			font-size: 22rpx;
			color: #4b5563;
			overflow: hidden;
			text-overflow: ellipsis;
			white-space: nowrap;
			max-width: 590rpx;
		}

		&:hover {
			background: #f9fafb;
			border-color: #e5e7eb;
		}
	}
}
</style>
